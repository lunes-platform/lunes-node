package io.lunes.settings

case class FeaturesSettings(autoShutdownOnUnsupportedFeature: Boolean,
                            supported: List[Short])
