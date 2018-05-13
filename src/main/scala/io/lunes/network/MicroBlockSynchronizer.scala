package io.lunes.network

import java.util.concurrent.TimeUnit

import com.google.common.cache.{Cache, CacheBuilder}
import io.lunes.metrics.BlockStats
import io.lunes.settings.SynchronizationSettings.MicroblockSynchronizerSettings
import io.lunes.state2.ByteStr
import io.netty.channel._
import monix.eval.{Coeval, Task}
import monix.execution.CancelableFuture
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import scorex.block.Block.BlockId
import scorex.block.MicroBlock

import scala.collection.mutable.{Set => MSet}
import scala.concurrent.duration.FiniteDuration

/** Microblock Synchronizer Object. */
object MicroBlockSynchronizer {
  /**
    * Factory for Tuple (Observable[(Channel, MicroblockData)], Coeval[CacheSizes]).
    * @param settings [[io.lunes.settings.SynchronizationSettings.MicroblockSynchronizerSettings]] for the factory.
    * @param peerDatabase [[io.lunes.network.PeerDatabase]] Input.
    * @param lastBlockIdEvents Last Block ID Events.
    * @param microblockInvs Inputs a Channel Observable for [[MicroBlockInv]].
    * @param microblockResponses Inputs a Channel Observable for [[MicroBlockResponse]].
    * @param scheduler The Service Scheduler.
    * @return Returns the Tuple.
    */
  def apply(settings: MicroblockSynchronizerSettings,
            peerDatabase: PeerDatabase,
            lastBlockIdEvents: Observable[ByteStr],
            microblockInvs: ChannelObservable[MicroBlockInv],
            microblockResponses: ChannelObservable[MicroBlockResponse],
            scheduler: SchedulerService): (Observable[(Channel, MicroblockData)], Coeval[CacheSizes]) = {

    implicit val schdlr: SchedulerService = scheduler

    val microBlockOwners = cache[MicroBlockSignature, MSet[Channel]](settings.invCacheTimeout)
    val nextInvs = cache[MicroBlockSignature, MicroBlockInv](settings.invCacheTimeout)
    val awaiting = cache[MicroBlockSignature, MicroBlockInv](settings.invCacheTimeout)
    val successfullyReceived = cache[MicroBlockSignature, Object](settings.processedMicroBlocksCacheTimeout)

    val lastBlockId = lastObserved(lastBlockIdEvents)

	  /** Gets the Owners of the MicroBlock.
		  * @param totalResBlockSig Total Block Signature.
		  * @return a Set for Channel.
		  */
    def owners(totalResBlockSig: BlockId): Set[Channel] = Option(microBlockOwners.getIfPresent(totalResBlockSig)).getOrElse(MSet.empty).toSet

	  /** Check if the Request has already been placed.
		  * @param totalSig Total Block Signature.
		  * @return True if the Request was placed.
		  */
    def alreadyRequested(totalSig: MicroBlockSignature): Boolean = Option(awaiting.getIfPresent(totalSig)).isDefined

	  /** Check if it has already benn Processed.
		  * @param totalSig Total Block Signature.
		  * @return True if it was processed.
		  */
    def alreadyProcessed(totalSig: MicroBlockSignature): Boolean = Option(successfullyReceived.getIfPresent(totalSig)).isDefined

    val cacheSizesReporter = Coeval.eval {
      CacheSizes(microBlockOwners.size(), nextInvs.size(), awaiting.size(), successfullyReceived.size())
    }

	  /** Requests Micro Block.
		  * @param mbInv Inputs the Micro Block Invocator.
		  * @return Returns a Cancelable Future for Unit.
		  */
    def requestMicroBlock(mbInv: MicroBlockInv): CancelableFuture[Unit] = {
      import mbInv.totalBlockSig

      def randomOwner(exclude: Set[Channel]) = random(owners(mbInv.totalBlockSig) -- exclude)

      //TODO:tailrec
      def task(attemptsAllowed: Int, exclude: Set[Channel]): Task[Unit] = Task.unit.flatMap { _ =>
        if (attemptsAllowed <= 0 || alreadyProcessed(totalBlockSig)) Task.unit
        else randomOwner(exclude).fold(Task.unit) { channel =>
          if (channel.isOpen) {
            val request = MicroBlockRequest(totalBlockSig)
            channel.writeAndFlush(request)
            awaiting.put(totalBlockSig, mbInv)
            task(attemptsAllowed - 1, exclude + channel).delayExecution(settings.waitResponseTimeout)
          } else task(attemptsAllowed, exclude + channel)
        }
      }

      task(MicroBlockDownloadAttempts, Set.empty).runAsyncLogErr
    }

	  /** Tries to load Next Block
		  * @param prevBlockId Previous Block ID [[ByteStr]].
		  */
    def tryDownloadNext(prevBlockId: ByteStr): Unit = Option(nextInvs.getIfPresent(prevBlockId)).foreach(requestMicroBlock)

    lastBlockIdEvents.mapTask(f => Task(tryDownloadNext(f))).executeOn(scheduler).logErr.subscribe()

    microblockInvs.mapTask { case ((ch, mbInv@MicroBlockInv(_, totalSig, prevSig, _))) => Task {
      mbInv.signaturesValid() match {
        case Left(err) =>
          peerDatabase.blacklistAndClose(ch, err.toString)
        case Right(_) =>
          microBlockOwners.get(totalSig, () => MSet.empty) += ch
          nextInvs.get(prevSig, { () =>
            BlockStats.inv(mbInv, ch)
            mbInv
          })
          lastBlockId()
            .filter(_ == prevSig && !alreadyRequested(totalSig))
            .foreach(tryDownloadNext)
      }
    }
    }.executeOn(scheduler).logErr.subscribe()

    val observable = microblockResponses.observeOn(scheduler).flatMap { case ((ch, MicroBlockResponse(mb))) =>
      import mb.{totalResBlockSig => totalSig}
      successfullyReceived.put(totalSig, dummy)
      BlockStats.received(mb, ch)
      Option(awaiting.getIfPresent(totalSig)) match {
        case None => Observable.empty
        case Some(mi) =>
          awaiting.invalidate(totalSig)
          Observable((ch, MicroblockData(Option(mi), mb, Coeval.evalOnce(owners(totalSig)))))
      }
    }
    (observable, cacheSizesReporter)
  }

  /** Holds Data for Microblock.
	  * @constructor Creates a new Microblock Data object.
    * @param invOpt Micro Block Invocator Options.
    * @param microBlock Micro Block.
    * @param microblockOwners Micro Blocks Owners.
    */
  case class MicroblockData(invOpt: Option[MicroBlockInv], microBlock: MicroBlock, microblockOwners: Coeval[Set[Channel]])

  type MicroBlockSignature = ByteStr

  private val MicroBlockDownloadAttempts = 2

  /** Generates a Random Object.
    * @param s Seed Set.
    * @tparam T Parametrized Type.
    * @return Returns an Option for the Parameter.
    */
  def random[T](s: Set[T]): Option[T] = if (s.isEmpty) None else {
    val n = util.Random.nextInt(s.size)
    s.drop(n).headOption
  }

  /** Generates a Cache with time limitations.
    * @param timeout Sets Timeout.
    * @tparam K Type Parameter under AnyRef Hierarchy.
    * @tparam V Type Parameter under AnyRef Hierarchy.
    * @return Returns a Cache Object.
    */
  def cache[K <: AnyRef, V <: AnyRef](timeout: FiniteDuration): Cache[K, V] = CacheBuilder.newBuilder()
    .expireAfterWrite(timeout.toMillis, TimeUnit.MILLISECONDS)
    .build[K, V]()

  /** Holds data for Cache Size
    * @param microBlockOwners Microblock Owners.
    * @param nextInvs Next Invs.
    * @param awaiting Awaiting Time.
    * @param successfullyReceived Size of successifully received elements.
    */
  case class CacheSizes(microBlockOwners: Long, nextInvs: Long, awaiting: Long, successfullyReceived: Long)

  private val dummy = new Object()
}
