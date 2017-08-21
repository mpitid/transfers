
package money.transfers

import java.time.Instant
import java.util.Currency

import io.jvm.uuid.UUID


package object data {

  type Offset = Int
  type UserID = UUID
  type AccountID = UUID
  type TransactionID = UUID

  type EventID = UUID

  type Amount = BigInt

  //
  // Domain objects
  //

  case class User(
      id: UUID
    , name: String
    , created: Instant
  )

  case class Account(
      id: AccountID
    , balance: Amount
    , currency: Currency
    , name: String
    , owner: User
    , created: Instant
  )

  case class TxStep(
      event: EventID
    , account: AccountID
    , amount: Amount
  ) {
    def undo: TxStep = TxStep(event, account, -amount)
  }

  case class Transaction(
      id: TransactionID
    , event: TransactionCreated
    , created: Instant
    , settled: Option[Instant] = None
    , error: Option[String] = None
  )

  //
  // Domain Events
  //

  sealed trait DomainEvent {
    def id: EventID
  }

  case class UserCreated(
      id: EventID
    , name: String
    , created: Instant
  ) extends DomainEvent

  case class AccountCreated(
      id: EventID
    , currency: Currency
    , name: String
    , created: Instant
    , userId: UserID
  ) extends DomainEvent

  case class TransactionCreated(
      id: EventID
    , received: Instant
    , details: TransactionDetails
  ) extends DomainEvent {
    def steps: Seq[TxStep] = {
      details.steps(id)
    }
  }

  sealed trait TransactionDetails {
    def steps(ev: EventID): Seq[TxStep]
    def matchCurrencies: Seq[(AccountID, AccountID)] = Seq()
  }

  case class TxDeposit(
    amount: Amount
    , dst: AccountID
    , ref: String
  ) extends TransactionDetails {
    require(amount > 0, "deposit amount must be > 0")
    def steps(ev: EventID): Seq[TxStep] = {
      Seq(TxStep(ev, dst, amount))
    }
  }

  case class TxTransfer(
    amount: Amount
    , src: AccountID
    , dst: AccountID
  ) extends TransactionDetails {
    require(amount > 0, "transfer amount must be > 0")
    def steps(ev: EventID): Seq[TxStep] = {
      Seq(
          TxStep(ev, src, -amount)
        , TxStep(ev, dst,  amount)
      )
    }
    override def matchCurrencies = Seq((src, dst))
  }

  /*
  // More transaction types can be encoded, for instance a currency
  // exchange transaction, where both parties have accounts in each currency:
  case class TxExchange(
      amount1: Amount
    , amount2: Amount
    , src1: AccountID
    , dst1: AccountID
    , src2: AccountID
    , dst2: AccountID
  ) extends TransactionDetails {
    override def matchCurrencies = Seq((src1, dst1), (src2, dst2))
    def steps(ev: EventID) = Seq(
        TxStep(ev, src1, -amount1)
      , TxStep(ev, dst1, amount1)
      , TxStep(ev, src2, amount2)
      , TxStep(ev, dst2, -amount2)
    )
  }
  */
}
