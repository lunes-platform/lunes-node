package io.lunes.network

import java.util.concurrent.TimeUnit

import cats._
import cats.implicits._
import com.google.common.cache.CacheBuilder
import io.netty.channel._
import monix.eval.Coeval
import monix.execution.Scheduler
import monix.reactive.Observable
import io.lunes.transaction.History.BlockchainScore
import scorex.utils.ScorexLogging

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

/** Rx Score Observer Object. */
object RxScoreObserver extends ScorexLogging {

  /** Holds data for the Best Channel
    * @param channel Netty Channel
    * @param score Inputs [[io.lunes.transaction.History.BlockchainScore]].
    */
  case class BestChannel(channel: Channel, score: BlockchainScore) {
    override def toString: String = s"BestChannel(${id(channel)},score: $score)"
  }

  implicit val bestChannelEq: Eq[BestChannel] = { (x, y) => x.channel == y.channel && x.score == y.score }
  /** Type for a Option for BestChannel */
  type SyncWith = Option[BestChannel]

  /** Holds data for Channel Close and Sync With
    * @param closed Inputs a Option for Netty Channel.
    * @param syncWith Inputs the SyncWith object.
    */
  case class ChannelClosedAndSyncWith(closed: Option[Channel], syncWith: SyncWith)

  implicit val channelClosedAndSyncWith: Eq[ChannelClosedAndSyncWith] = { (x, y) =>
    x.closed == y.closed && x.syncWith == y.syncWith
  }

  /** Calculate Sync
    * @param bestChannel Inputs a Option Channel.
    * @param localScore Local Blockchain Score.
    * @param scoreMap Maps a Channel to a BlockchainScore.
    * @return
    */
  private def calcSyncWith(bestChannel: Option[Channel], localScore: BlockchainScore, scoreMap: scala.collection.Map[Channel, BlockchainScore]): SyncWith = {
    val (bestScore, bestScoreChannels) = scoreMap.foldLeft(BigInt(0) -> List.empty[Channel]) {
      case (r@(maxScore, maxScoreChannels), (currScoreChannel, currScore)) =>
        if (currScore > maxScore) currScore -> List(currScoreChannel)
        else if (currScore == maxScore) maxScore -> (currScoreChannel :: maxScoreChannels)
        else r
    }

    if (bestScore > localScore && bestScoreChannels.nonEmpty) bestChannel match {
      case Some(c) if bestScoreChannels.contains(c) => Some(BestChannel(c, bestScore))
      case _ =>
        val head = bestScoreChannels.head
        log.trace(s"${id(head)} Publishing new best channel with score=$bestScore > localScore $localScore")
        Some(BestChannel(head, bestScore))
    } else None
  }

  /** Application function of the RxScoreOberver.
    * @param scoreTtl Input Score.
    * @param remoteScoreDebounce Input Remote Score.
    * @param initalLocalScore Initial Local Score.
    * @param localScores Observes local Blockchain Scores.
    * @param remoteScores Channel Observer Blockchain Scores.
    * @param channelClosed Channel Closed Observable.
    * @param channelTimeout Channel Timeout Observable.
    * @param scheduler Object Scheduler.
    * @return
    */
  def apply(scoreTtl: FiniteDuration,
            remoteScoreDebounce: FiniteDuration,
            initalLocalScore: BigInt,
            localScores: Observable[BlockchainScore],
            remoteScores: ChannelObservable[BlockchainScore],
            channelClosed: Observable[Channel],
            channelTimeout: Observable[Channel],
            scheduler: Scheduler): (Observable[ChannelClosedAndSyncWith], Coeval[Stats]) = {

    var localScore: BlockchainScore = initalLocalScore
    var currentBestChannel: Option[Channel] = None
    val scores = CacheBuilder.newBuilder()
      .expireAfterWrite(scoreTtl.toMillis, TimeUnit.MILLISECONDS)
      .build[Channel, BlockchainScore]()
    val statsReporter = Coeval.eval {
      Stats(localScore, currentBestChannel.toString, scores.size())
    }

    /**
      *
      * @return
      */
    def ls: Observable[Option[Channel]] = localScores
      .observeOn(scheduler)
      .distinctUntilChanged
      .map { x =>
        log.debug(s"New local score: $x, old: $localScore, Δ${x - localScore}")
        localScore = x
        None
      }

    // Make a stream of unique scores in each channel
    /**
      *
      * @return
      */
    def rs: Observable[Option[Channel]] = remoteScores
      .observeOn(scheduler)
      .groupBy(_._1)
      .map(_
        .distinctUntilChanged
        .debounce(remoteScoreDebounce)
      )
      .merge
      .map { case ((ch, score)) =>
        scores.put(ch, score)
        log.trace(s"${id(ch)} New remote score $score")
        None
      }

    /**
      *
      * @return
      */
    def cc: Observable[Option[Channel]] = Observable.merge(channelClosed, channelTimeout)
      .observeOn(scheduler)
      .map { ch =>
        scores.invalidate(ch)
        if (currentBestChannel.contains(ch)) {
          log.debug(s"${id(ch)} Best channel has been closed")
          currentBestChannel = None
        }
        Option(ch)
      }

    val observable = Observable
      .merge(ls, rs, cc)
      .map { maybeClosedChannel =>
        val sw = calcSyncWith(currentBestChannel, localScore, scores.asMap().asScala)
        currentBestChannel = sw.map(_.channel)
        ChannelClosedAndSyncWith(maybeClosedChannel, sw)
      }
      .logErr
      .distinctUntilChanged
      .share(scheduler)

    (observable, statsReporter)
  }

  /** Holds data for Statistics
    * @param localScore Local Blockchain Score.
    * @param currentBestChannel String for current Best Channel.
    * @param scoresCacheSize Score Cache Size.
    */
  case class Stats(localScore: BlockchainScore, currentBestChannel: String, scoresCacheSize: Long)

}