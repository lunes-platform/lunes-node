package io.lunes.network

import java.net.{InetAddress, InetSocketAddress}

import io.netty.channel.Channel
import scorex.utils.ScorexLogging

/**
  *
  */
trait PeerDatabase extends AutoCloseable {
  /**
    *
    * @param socketAddress
    * @return
    */
  def addCandidate(socketAddress: InetSocketAddress): Boolean

  /**
    *
    * @param socketAddress
    */
  def touch(socketAddress: InetSocketAddress): Unit

  /**
    *
    * @param host
    * @param reason
    */
  def blacklist(host: InetSocketAddress, reason: String): Unit

  /**
    *
    * @return
    */
  def knownPeers: Map[InetSocketAddress, Long]

  /**
    *
    * @return
    */
  def blacklistedHosts: Set[InetAddress]

  /**
    *
    * @return
    */
  def suspendedHosts: Set[InetAddress]

  /**
    *
    * @param excluded
    * @return
    */
  def randomPeer(excluded: Set[InetSocketAddress]): Option[InetSocketAddress]

  /**
    *
    * @return
    */
  def detailedBlacklist: Map[InetAddress, (Long, String)]

  /**
    *
    * @return
    */
  def detailedSuspended: Map[InetAddress, Long]

  /**
    *
    */
  def clearBlacklist(): Unit

  /**
    *
    * @param host
    */
  def suspend(host: InetSocketAddress): Unit

  /**
    *
    * @param channel
    * @param reason
    */
  def blacklistAndClose(channel: Channel, reason: String): Unit

  /**
    *
    * @param channel
    */
  def suspendAndClose(channel: Channel): Unit
}

/**
  *
  */
object PeerDatabase extends ScorexLogging {

  /**
    *
    */
  trait NoOp extends PeerDatabase {
    /**
      *
      * @param socketAddress
      * @return
      */
    override def addCandidate(socketAddress: InetSocketAddress): Boolean = true

    /**
      *
      * @param socketAddress
      */
    override def touch(socketAddress: InetSocketAddress): Unit = {}

    /**
      *
      * @param host
      * @param reason
      */
    override def blacklist(host: InetSocketAddress, reason: String): Unit = {}

    /**
      *
      * @return
      */
    override def knownPeers: Map[InetSocketAddress, Long] = Map.empty

    /**
      *
      * @return
      */
    override def blacklistedHosts: Set[InetAddress] = Set.empty

    /**
      *
      * @param excluded
      * @return
      */
    override def randomPeer(excluded: Set[InetSocketAddress]): Option[InetSocketAddress] = None

    /**
      *
      * @return
      */
    override def detailedBlacklist: Map[InetAddress, (Long, String)] = Map.empty

    /**
      *
      */
    override def clearBlacklist(): Unit = ()

    /**
      *
      * @param host
      */
    override def suspend(host: InetSocketAddress): Unit = {}

    /**
      *
      */
    override val suspendedHosts: Set[InetAddress] = Set.empty
    /**
      *
      */
    override val detailedSuspended: Map[InetAddress, Long] = Map.empty

    /**
      *
      * @param channel
      * @param reason
      */
    override def blacklistAndClose(channel: Channel, reason: String): Unit = channel.close()

    /**
      *
      * @param channel
      */
    override def suspendAndClose(channel: Channel): Unit = channel.close()

    /**
      *
      */
    override def close(): Unit = {}
  }

  /**
    *
    */
  object NoOp extends NoOp

}
