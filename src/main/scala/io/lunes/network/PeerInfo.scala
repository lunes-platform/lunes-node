package io.lunes.network

import java.net.{InetSocketAddress, SocketAddress}

/**
	*
	* @param remoteAddress
	* @param declaredAddress
	* @param applicationName
	* @param applicationVersion
	* @param nodeName
	* @param nodeNonce
	*/
case class PeerInfo(
    remoteAddress: SocketAddress,
    declaredAddress: Option[InetSocketAddress],
    applicationName: String,
    applicationVersion: (Int, Int, Int),
    nodeName: String,
    nodeNonce: Long)
