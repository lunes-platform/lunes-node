package io.lunes.mining

import scorex.block.Block
import io.lunes.transaction.Transaction

/** Mining Constraints Trait. */
trait MiningConstraint {
  /** Interface for Empty Check.
    * @return Returns True if Empty.
    */
  def isEmpty: Boolean

  /** Interface for Overfilled Check.
    * @return Returns True if its Overfilled.
    */
  def isOverfilled: Boolean

  /** Interface for [[scorex.block.Block]] insertion.
    * @param x [[scorex.block.Block]] for input.
    * @return Returns a Mining Constraint.
    */
  def put(x: Block): MiningConstraint

  /** Interface for [[io.lunes.transaction.Transaction]] insertion.
    * @param x [[io.lunes.transaction.Transaction]] for input.
    * @return Returns a Mining Constraint.
    */
  def put(x: Transaction): MiningConstraint
}

/** Case Class for One Dimensional Mining Constraint implementing MiningConstraint interface.
  * @constructor Creates a new One Dimensional Mining Constraint object.
  * @param rest The Rest.
  * @param estimator An estimator for the constraint.
  */
case class OneDimensionalMiningConstraint(rest: Long, estimator: Estimator) extends MiningConstraint {
  /** Implements [[MiningConstraint.isEmpty]].
    * @return
    */
  override def isEmpty: Boolean = rest <= 0

  /** Implements [[MiningConstraint.isOverfilled]]
    * @return
    */
  override def isOverfilled: Boolean = rest < 0

  /** Implements [[MiningConstraint.put]] for a Block input.
    * @param x Input the Block.
    * @return Returns an One Dimensional Mining Constraint.
    */
  override def put(x: Block): OneDimensionalMiningConstraint = put(estimator.estimate(x))

  /** Implements [[MiningConstraint.put]] for a Transaction input.
    * @param x Input the Transaction.
    * @return Returns an One Dimensional Mining Constraint.
    */
  override def put(x: Transaction): OneDimensionalMiningConstraint = put(estimator.estimate(x))

  /** Gets a One Dimensional Mining Constraint given a Long input.
    * @param x The Long input.
    * @return Returns an One Dimensional Mining Constraint.
    */
  private def put(x: Long): OneDimensionalMiningConstraint = copy(rest = this.rest - x)
}

/** Companion Object for [[OneDimensionalMiningConstraint]]. */
object OneDimensionalMiningConstraint {
  def full(estimator: Estimator): OneDimensionalMiningConstraint = new OneDimensionalMiningConstraint(estimator.max, estimator)
}

/** Case Class for Two Dimensional Mining Constraint implementing MiningConstraint interface.
  * @param first Inputs the first [[MiningConstraint]]
  * @param second Inputs the second [[MiningConstraint]]
  */
case class TwoDimensionalMiningConstraint(first: MiningConstraint, second: MiningConstraint) extends MiningConstraint {
  /** Implements [[MiningConstraint.isEmpty]].
    * @return Returns true if it is empty.
    */
  override def isEmpty: Boolean = first.isEmpty || second.isEmpty

  /** Implements [[MiningConstraint.isOverfilled]].
    * @return Returns true if it is overfilled
    */
  override def isOverfilled: Boolean = first.isOverfilled || second.isOverfilled

  /** Implements [[MiningConstraint.put]] given a Block.
    * @param x The input [[Block]].
    * @return Returns a [[TwoDimensionalMiningConstraint]].
    */
  override def put(x: Block): TwoDimensionalMiningConstraint = TwoDimensionalMiningConstraint(first.put(x), second.put(x))

  /** Implements [[MiningConstraint.put]] given a Transaction.
    * @param x The Transaction.
    * @return Returns a [[TwoDimensionalMiningConstraint]].
    */
  override def put(x: Transaction): TwoDimensionalMiningConstraint = TwoDimensionalMiningConstraint(first.put(x), second.put(x))
}

/** The companion object for TwoDimensionalMiningConstraint. */
object TwoDimensionalMiningConstraint {
  /** Factory for [[TwoDimensionalMiningConstraint]] based on two Estimators.
    * @param first
    * @param second
    * @return
    */
  def full(first: Estimator, second: Estimator): TwoDimensionalMiningConstraint = new TwoDimensionalMiningConstraint(
    first = OneDimensionalMiningConstraint.full(first),
    second = OneDimensionalMiningConstraint.full(second)
  )

  /** Factory for [[TwoDimensionalMiningConstraint]] based on [[MiningConstraint]] based objects.
    * @param firstRest
    * @param secondRest
    * @return
    */
  def partial(firstRest: MiningConstraint, secondRest: MiningConstraint): TwoDimensionalMiningConstraint = new TwoDimensionalMiningConstraint(firstRest, secondRest)
}
