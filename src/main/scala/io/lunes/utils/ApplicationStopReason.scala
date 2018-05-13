package io.lunes.utils

/**
	*
	* @param code
	*/
sealed abstract class ApplicationStopReason(val code: Int)//poderia ser sealed trait

/**
	*
	*/
case object Default extends ApplicationStopReason(1)

/**
	*
	*/
case object UnsupportedFeature extends ApplicationStopReason(38)
