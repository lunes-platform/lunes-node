package io.lunes.transaction

/**
  *
  */
trait Proven extends Authorized {
  /**
    *
     * @return
    */
  def proofs: Proofs
}
