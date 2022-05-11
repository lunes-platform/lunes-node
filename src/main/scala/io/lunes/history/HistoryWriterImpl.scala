package io.lunes.history

import java.util.concurrent.locks.ReentrantReadWriteLock

import com.google.common.primitives.{Ints, Shorts}
import io.lunes.core.storage.db._
import io.lunes.features.{FeatureProvider, FeaturesProperties}
import io.lunes.settings.{FeaturesSettings, FunctionalitySettings}
import io.lunes.state2._
import io.lunes.utils._
import kamon.Kamon
import org.iq80.leveldb.{DB, WriteBatch}
import scorex.block.{Block, BlockHeader}
import io.lunes.transaction.History.BlockchainScore
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction._
import scorex.utils.Synchronized.WriteLock
import scorex.utils.{NTP, ScorexLogging, Time}

import scala.util.Try

/** Lunes History Writer Implementor.
  * @constructor Creates a History Writer Implementator for Lunes.
  * @param db The LevelDB Database.
  * @param synchronizationToken Inputs the Syncronizaiton Token.
  * @param functionalitySettings Inputs the Functionality Settings.
  * @param featuresSettings Input the Feature Settings.
  * @param time Sets Time Stamp.
  */
class HistoryWriterImpl private(db: DB, val synchronizationToken: ReentrantReadWriteLock,
                                functionalitySettings: FunctionalitySettings, featuresSettings: FeaturesSettings, time: Time)
  extends SubStorage(db, "history") with PropertiesStorage with VersionedStorage with History with FeatureProvider with ScorexLogging {

  override protected val Version: Int = 1

  import HistoryWriterImpl._

  private val BlockAtHeightPrefix = "blocks".getBytes(Charset)
  private val SignatureAtHeightPrefix = "signatures".getBytes(Charset)
  private val HeightBySignaturePrefix = "heights".getBytes(Charset)
  private val ScoreAtHeightPrefix = "scores".getBytes(Charset)
  private val VotesAtHeightPrefix = "votes".getBytes(Charset)
  private val FeatureStatePrefix = "features".getBytes(Charset)
  private val FeaturesIndexKey = makeKey("feature-index".getBytes(Charset), 0)

  private val HeightProperty = "history-height"

  private val featuresProperties = FeaturesProperties(functionalitySettings)

  /** Gets the Activation Window Size.
    * @param h Inputs Height.
    * @return Returns the Window Size.
    */
  override def activationWindowSize(h: Int): Int = featuresProperties.featureCheckBlocksPeriodAtHeight(h)

  /** Gets the minimum votes for Activate a Feature within the Window.
    * @param h Inputs Height.
    * @return Return Minimum Votes at the Height.
    */
  def minVotesWithinWindowToActivateFeature(h: Int): Int = featuresProperties.blocksForFeatureActivationAtHeight(h)

  private lazy val preAcceptedFeatures = functionalitySettings.preActivatedFeatures.mapValues(h => h - activationWindowSize(h))

  @volatile private var heightInfo: HeightInfo = (height(), time.getTimestamp())

  /** Gets the Approved Features.
    * @return Returns the Approved Features.
    */
  override def approvedFeatures(): Map[Short, Int] = read { implicit lock =>
    preAcceptedFeatures ++ getFeaturesState
  }

  /** Gets the features votes count within the Activation Window.
    * @param height Inputs the height.
    * @return Return the feature votes.
    */
  override def featureVotesCountWithinActivationWindow(height: Int): Map[Short, Int] = read { implicit lock =>
    val votingWindowOpening = FeatureProvider.votingWindowOpeningFromHeight(height, activationWindowSize(height))
    get(makeKey(VotesAtHeightPrefix, votingWindowOpening)).map(VotesMapCodec.decode).map(_.explicitGet().value).getOrElse(Map.empty)
  }

  /** Alter the votes.
    * @param height Inputs the Height.
    * @param votes Input the Votes.
    * @param voteMod Sets the Vote Method.
    * @param batch Inputs an Option for LevelDB WriteBatch.
    */
  private def alterVotes(height: Int, votes: Set[Short], voteMod: Int, batch: Option[WriteBatch]): Unit = write("alterVotes") { implicit lock =>
    val votingWindowOpening = FeatureProvider.votingWindowOpeningFromHeight(height, activationWindowSize(height))
    val votesWithinWindow = featureVotesCountWithinActivationWindow(height)
    val newVotes = votes.foldLeft(votesWithinWindow)((v, feature) => v + (feature -> (v.getOrElse(feature, 0) + voteMod)))
    put(makeKey(VotesAtHeightPrefix, votingWindowOpening), VotesMapCodec.encode(newVotes), batch)
  }

  /** Appends a Block.
    * @param block [[scorex.block.Block]] to append.
    * @param acceptedFeatures Set of accepted Features.
    * @param consensusValidation Consensus Validation Map.
    * @return Returns Either a [[io.lunes.state2.BlockDiff]] (case Success) or a [[io.lunes.transaction.ValidationError]].
    */
  def appendBlock(block: Block, acceptedFeatures: Set[Short])(consensusValidation: => Either[ValidationError, BlockDiff]): Either[ValidationError, BlockDiff] =
    write("appendBlock") { implicit lock =>
      val b = createBatch()

      assert(block.signaturesValid().isRight)

      if ((height() == 0) || (this.lastBlock.get.uniqueId == block.reference)) consensusValidation.map { blockDiff =>
        val h = height() + 1
        val score = (if (height() == 0) BigInt(0) else this.score()) + block.blockScore()
        put(makeKey(BlockAtHeightPrefix, h), block.bytes(), b)
        put(makeKey(ScoreAtHeightPrefix, h), score.toByteArray, b)
        put(makeKey(SignatureAtHeightPrefix, h), block.uniqueId.arr, b)
        put(makeKey(HeightBySignaturePrefix, block.uniqueId.arr), Ints.toByteArray(h), b)
        setHeight(h, b)

        val presentFeatures = allFeatures().toSet
        val newFeatures = acceptedFeatures.diff(presentFeatures)
        newFeatures.foreach(f => addFeature(f, h, b))
        alterVotes(h, block.featureVotes, 1, b)

        blockHeightStats.record(h)
        blockSizeStats.record(block.bytes().length)
        transactionsInBlockStats.record(block.transactionData.size)

        commit(b)
        log.trace(s"Full $block(id=${block.uniqueId} persisted")

        blockDiff
      }
      else {
        Left(GenericError(s"Parent ${block.reference} of block ${block.uniqueId} does not match last block ${this.lastBlock.map(_.uniqueId)}"))
      }
    }

  /** Return All features.
    * @return Returns a Sequence of the features.
    */
  private def allFeatures(): Seq[Short] = read { implicit lock =>
    get(FeaturesIndexKey).map(ShortSeqCodec.decode).map(_.explicitGet().value).getOrElse(Seq.empty[Short])
  }

  /** Adds a Feature
    * @param featureId Inputs the Feature ID.
    * @param height Inputs the Height.
    * @param batch Inputs a LevelDB WriteBatch Object.
    */
  private def addFeature(featureId: Short, height: Int, batch: Option[WriteBatch]): Unit = {
    val features = (allFeatures() :+ featureId).distinct
    put(makeKey(FeatureStatePrefix, Shorts.toByteArray(featureId)), Ints.toByteArray(height), batch)
    put(FeaturesIndexKey, ShortSeqCodec.encode(features), batch)
  }

  /** Deletes a Feature
    * @param featureId Inputs the Feature ID.
    * @param batch Inputs a LevelDB WriteBatch Object.
    */
  private def deleteFeature(featureId: Short, batch: Option[WriteBatch]): Unit = {
    val features = allFeatures().filterNot(f => f == featureId).distinct
    delete(makeKey(FeatureStatePrefix, Shorts.toByteArray(featureId)), batch)
    put(FeaturesIndexKey, ShortSeqCodec.encode(features), batch)
  }

  /** Gets a Feature Height.
    * @param featureId Inputs the Feature ID.
    * @return Returns an Option for the Height.
    */
  private def getFeatureHeight(featureId: Short): Option[Int] =
    get(makeKey(FeatureStatePrefix, Shorts.toByteArray(featureId))).flatMap(b => Try(Ints.fromByteArray(b)).toOption)

  /** Gets Feature State.
    * @return Returns a Map for the Feature.
    */
  private def getFeaturesState(): Map[Short, Int] = {
    allFeatures().foldLeft(Map.empty[Short, Int]) { (r, f) =>
      val h = getFeatureHeight(f)
      if (h.isDefined) r.updated(f, h.get) else r
    }
  }

  /** Discards the Block;
    * @return Returns an Option for the [[scorex.block.Block]].
    */
  def discardBlock(): Option[Block] = write("discardBlock") { implicit lock =>
    val h = height()

    val b = createBatch()

    alterVotes(h, blockAt(h).map(b => b.featureVotes).getOrElse(Set.empty), -1, b)

    val key = makeKey(BlockAtHeightPrefix, h)
    val maybeBlockBytes = get(key)
    val tryDiscardedBlock = maybeBlockBytes.map(b => Block.parseBytes(b))
    val maybeDiscardedBlock = tryDiscardedBlock.flatMap(_.toOption)


    delete(key, b)
    delete(makeKey(ScoreAtHeightPrefix, h), b)

    if (h % activationWindowSize(h) == 0) {
      allFeatures().foreach { f =>
        val featureHeight = getFeatureHeight(f)
        if (featureHeight.isDefined && featureHeight.get == h) deleteFeature(f, b)
      }
    }

    val signatureKey = makeKey(SignatureAtHeightPrefix, h)
    get(signatureKey).foreach(a => delete(makeKey(HeightBySignaturePrefix, a), b))
    delete(signatureKey, b)

    setHeight(h - 1, b)

    commit(b)

    maybeDiscardedBlock
  }

  /** Gets the Last Block Ids.
    * @param howMany Inputs maximum number of IDs.
    * @return Return a Sequence of IDs.
    */
  override def lastBlockIds(howMany: Int): Seq[ByteStr] = read { implicit lock =>
    val startHeight = Math.max(1, height - howMany + 1)
    (startHeight to height).flatMap(getBlockSignature).reverse
  }

  /** Gets the Height.
    * @return Returns the Height.
    */
  override def height(): Int = read { implicit lock =>
    getIntProperty(HeightProperty).getOrElse(0)
  }

  /** Sets the Height.
    * @param x Height.
    * @param batch Inputs Option for LevelDB WriteBatch.
    * @param lock Sets [[scorex.utils.Synchronized.WriteLock]] Object.
    */
  private def setHeight(x: Int, batch: Option[WriteBatch])(implicit lock: WriteLock): Unit = {
    putIntProperty(HeightProperty, x, batch)
    heightInfo = (x, time.getTimestamp())
  }

  /** Get the Score for given ID.
    * @param id Inputs ID.
    * @return Returns Option for BlockchanScore.
    */
  override def scoreOf(id: ByteStr): Option[BlockchainScore] = read { implicit lock =>
    val maybeHeight = heightOf(id)
    if (maybeHeight.isDefined) {
      val maybeScoreBytes = get(makeKey(ScoreAtHeightPrefix, maybeHeight.get))
      if (maybeScoreBytes.isDefined) Some(BigInt(maybeScoreBytes.get)) else None
    } else None
  }

  /** Gets the Height for given Block Signature.
    * @param blockSignature Inputs Signature.
    * @return Returns an Option for Height.
    */
  override def heightOf(blockSignature: ByteStr): Option[Int] = read { implicit lock =>
    get(makeKey(HeightBySignaturePrefix, blockSignature.arr)).map(Ints.fromByteArray)
  }

  /** Gets the Block as an Array of Byte.
    * @param height Inputs the Height.
    * @return Returns an Option for Array Byte. It is the Raw content of the Block.
    */
  override def blockBytes(height: Int): Option[Array[Byte]] = read { implicit lock =>
    get(makeKey(BlockAtHeightPrefix, height))
  }

  /** Gets Last Block Timestamp.
    * @return Returns an Option Long as Timestamp.
    */
  override def lastBlockTimestamp(): Option[Long] = this.lastBlock.map(_.timestamp)

  /** Gets Last Block ID.
    * @return Returns an Option for the ID.
    */
  override def lastBlockId(): Option[ByteStr] = this.lastBlock.map(_.signerData.signature)

  /** Gets a Block at given Height.
    * @param height Inputs the height.
    * @return Returns an Option for [[scorex.block.Block]].
    */
  override def blockAt(height: Int): Option[Block] = blockBytes(height).map(Block.parseBytes(_).get)

  /** Gets a Block Header and its Size at given Height.
    * @param height Inputs the Height.
    * @return Returns a Tuple ([[scorex.block.BlockHeader]], Int).
    */
  override def blockHeaderAndSizeAt(height: Int): Option[(BlockHeader, Int)] =
    blockBytes(height).map(bytes => (BlockHeader.parseBytes(bytes).get._1, bytes.length))

  /** Gets Debug Information.
    * @return Returns a [[io.lunes.utils.HeightInfo]] as Debug Information.
    */
  override def debugInfo: HeightInfo = heightInfo

  /** Gets Block Signature at a given Height.
    * @param height Inputs Height.
    * @return Returns Block Signature.
    */
  private def getBlockSignature(height: Int): Option[ByteStr] = get(makeKey(SignatureAtHeightPrefix, height)).map(ByteStr.apply)
}

/** History Writer Implementor Companion Object*/
object HistoryWriterImpl extends ScorexLogging {
  /** Alternative Constructor Factory.
    * @constructor Returns an Option for a [[HistoryWriterImpl]] object.
    * @param db The LevelDB Database.
    * @param synchronizationToken Inputs the Syncronizaiton Token.
    * @param functionalitySettings Inputs the Functionality Settings.
    * @param featuresSettings Input the Feature Settings.
    * @param time Sets Time Stamp.
    * @return Returns a object reference.
    */
  def apply(db: DB, synchronizationToken: ReentrantReadWriteLock, functionalitySettings: FunctionalitySettings,
            featuresSettings: FeaturesSettings, time: Time = NTP): Try[HistoryWriterImpl] =
    createWithVerification[HistoryWriterImpl](new HistoryWriterImpl(db, synchronizationToken, functionalitySettings, featuresSettings, time))

  private val blockHeightStats = Kamon.metrics.histogram("block-height")
  private val blockSizeStats = Kamon.metrics.histogram("block-size-bytes")
  private val transactionsInBlockStats = Kamon.metrics.histogram("transactions-in-block")
}
