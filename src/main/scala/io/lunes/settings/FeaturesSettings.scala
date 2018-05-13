package io.lunes.settings

/**
	*
	* @param autoShutdownOnUnsupportedFeature
	* @param supported
	*/
case class FeaturesSettings(autoShutdownOnUnsupportedFeature: Boolean,
                            supported: List[Short])
