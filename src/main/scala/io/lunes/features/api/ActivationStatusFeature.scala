package io.lunes.features.api

import io.lunes.features.BlockchainFeatureStatus

case class ActivationStatusFeature(id: Short,
                                   blockchainStatus: BlockchainFeatureStatus,
                                   nodeStatus: NodeFeatureStatus,
                                   activationHeight: Option[Int],
                                   supportedBlocks: Option[Int])
