
package money.transfers.http

import money.transfers.data.{AccountID, UserID}

sealed trait ResourceEntity

object ResourceEntity {
  case class UserRequest(
      name: String
  ) extends ResourceEntity

  case class AccountRequest(
      user: UserID
    , name: String
    , currency: String
  ) extends ResourceEntity

  case class TransactionRequest(
      deposit: Option[DepositRequest] = None
    , transfer: Option[TransferRequest] = None
  ) extends ResourceEntity


  case class DepositRequest(
      amount: BigInt
    , dst: AccountID
    , ref: String
  )

  case class TransferRequest(
      amount: BigInt
    , src: AccountID
    , dst: AccountID
  )

  /** Generic response for async endpoints. */
  case class ResourceId(id: String)
}

