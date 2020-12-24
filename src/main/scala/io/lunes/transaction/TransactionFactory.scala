package io.lunes.transaction

import com.google.common.base.Charsets
import io.lunes.state2.ByteStr
import io.lunes.transaction.assets._
import io.lunes.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import scorex.account._
import scorex.api.http.alias.CreateAliasRequest
import scorex.api.http.assets._
import scorex.api.http.leasing.{LeaseCancelRequest, LeaseRequest}
import scorex.crypto.encode.Base58
import scorex.utils.Time
import scorex.wallet.Wallet

/**
  *
  */
object TransactionFactory {
  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def registryData(request: RegistryRequest, wallet: Wallet, time: Time): Either[ValidationError, RegistryTransaction] =
    for {
      senderPrivateKey <- wallet.findWallet(request.sender)
      recipientAcc <- AddressOrAlias.fromString(request.recipient)
      tx <- RegistryTransaction.create( senderPrivateKey,
          request.timestamp.getOrElse(time.getTimestamp()),
          request.fee,
          request.userdata.filter(_.nonEmpty).map(Base58.decode(_).get).getOrElse(Array.emptyByteArray))
    } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def transferAsset(request: TransferRequest, wallet: Wallet, time: Time): Either[ValidationError, TransferTransaction] =
    for {
      senderPrivateKey <- wallet.findWallet(request.sender)
      recipientAcc <- AddressOrAlias.fromString(request.recipient)
      tx <- TransferTransaction
        .create(request.assetId.map(s => ByteStr.decodeBase58(s).get),
          senderPrivateKey,
          recipientAcc,
          request.amount,
          request.timestamp.getOrElse(time.getTimestamp()),
          request.feeAssetId.map(s => ByteStr.decodeBase58(s).get),
          request.fee)
    } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def massTransferAsset(request: MassTransferRequest, wallet: Wallet, time: Time): Either[ValidationError, MassTransferTransaction] =
    for {
      senderPrivateKey <- wallet.findWallet(request.sender)
      transfers <- MassTransferTransaction.parseTransfersList(request.transfers)
      tx <- MassTransferTransaction.selfSigned(
        Proofs.Version,
        request.assetId.map(s => ByteStr.decodeBase58(s).get),
        senderPrivateKey,
        transfers,
        request.timestamp.getOrElse(time.getTimestamp()),
        request.fee)
    } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def issueAsset(request: IssueRequest, wallet: Wallet, time: Time): Either[ValidationError, IssueTransaction] =
    for {
      senderPrivateKey <- wallet.findWallet(request.sender)
      timestamp = request.timestamp.getOrElse(time.getTimestamp())
      tx <- IssueTransaction.create(senderPrivateKey,
        request.name.getBytes(Charsets.UTF_8),
        request.description.getBytes(Charsets.UTF_8),
        request.quantity, request.decimals, request.reissuable, request.fee, timestamp)
    } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def lease(request: LeaseRequest, wallet: Wallet, time: Time): Either[ValidationError, LeaseTransaction] = for {
    senderPrivateKey <- wallet.findWallet(request.sender)
    recipientAcc <- AddressOrAlias.fromString(request.recipient)
    timestamp = request.timestamp.getOrElse(time.getTimestamp())
    tx <- LeaseTransaction.create(senderPrivateKey, request.amount, request.fee, timestamp, recipientAcc)
  } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def leaseCancel(request: LeaseCancelRequest, wallet: Wallet, time: Time): Either[ValidationError, LeaseCancelTransaction] =
    for {
      pk <- wallet.findWallet(request.sender)
      timestamp = request.timestamp.getOrElse(time.getTimestamp())
      tx <- LeaseCancelTransaction.create(pk, ByteStr.decodeBase58(request.txId).get, request.fee, timestamp)
    } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def alias(request: CreateAliasRequest, wallet: Wallet, time: Time): Either[ValidationError, CreateAliasTransaction] = for {
    senderPrivateKey <- wallet.findWallet(request.sender)
    alias <- Alias.buildWithCurrentNetworkByte(request.alias)
    timestamp = request.timestamp.getOrElse(time.getTimestamp())
    tx <- CreateAliasTransaction.create(senderPrivateKey, alias, request.fee, timestamp)
  } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def reissueAsset(request: ReissueRequest, wallet: Wallet, time: Time): Either[ValidationError, ReissueTransaction] = for {
    pk <- wallet.findWallet(request.sender)
    timestamp = request.timestamp.getOrElse(time.getTimestamp())
    tx <- ReissueTransaction.create(pk, ByteStr.decodeBase58(request.assetId).get, request.quantity, request.reissuable, request.fee, timestamp)
  } yield tx

  /**
    *
    * @param request
    * @param wallet
    * @param time
    * @return
    */
  def burnAsset(request: BurnRequest, wallet: Wallet, time: Time): Either[ValidationError, BurnTransaction] = for {
    pk <- wallet.findWallet(request.sender)
    timestamp = request.timestamp.getOrElse(time.getTimestamp())
    tx <- BurnTransaction.create(pk, ByteStr.decodeBase58(request.assetId).get, request.quantity, request.fee, timestamp)
  } yield tx
}
