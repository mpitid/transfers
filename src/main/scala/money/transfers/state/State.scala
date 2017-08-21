
package money.transfers.state

import java.util.concurrent.atomic.AtomicReference

import money.transfers.data._
import money.transfers.util.RichAtomicReference

import scalaz.{-\/, \/, \/-}


trait StateReadable {
  def getUser(id: UserID): StateError \/ User
  def getAccount(id: AccountID): StateError \/ Account
  def getTransaction(id: TransactionID): StateError \/ Transaction
}

trait StateWritable {
  this: StateReadable =>

  def addUser(user: User): StateError \/ User
  def addAccount(account: Account): StateError \/ Account
  def addTransaction(transaction: Transaction): StateError \/ Transaction

  def applyTransaction(steps: Seq[TxStep]): StateError \/ Seq[Account]
  def matchCurrencies(ids: Seq[(AccountID, AccountID)]): StateError \/ Seq[(Account, Account)]
}


sealed trait StateError extends Throwable

object StateError {
  case class UserExists(id: UserID) extends StateError
  case class AccountExists(id: AccountID) extends StateError
  case class TransactionExists(id: TransactionID) extends StateError

  case class UserNotFound(id: UserID) extends StateError
  case class AccountNotFound(id: AccountID) extends StateError
  case class TransactionNotFound(id: TransactionID) extends StateError

  case class InsufficientFunds(account: Account, tx: TxStep) extends StateError
  case class CurrencyMismatch(a: Account, b: Account) extends StateError

  def transactionFailed(err: StateError): Option[String] = {
    err match {
      case InsufficientFunds(account, step) =>
        Some(s"insufficient funds: requested ${-step.amount} ${account.currency} from account ${account.id} with balance ${account.balance}")
      case CurrencyMismatch(a, b) =>
        Some(s"currency mismatch: ${a.id} ${a.currency} vs ${b.id} ${b.currency}")
      case _ =>
        None
    }
  }
}


/** In-memory implementation of the state interface. */
class State(
  init: Option[ImState] = None
) extends StateWritable
     with StateReadable {

  import scalaz.syntax.std.option._

  protected val db = new AtomicReference(init.getOrElse(ImState()))

  def getUser(id: UserID): StateError \/ User = {
    db.get().users.get(id).toRightDisjunction(StateError.UserNotFound(id))
  }

  def getAccount(id: AccountID): StateError \/ Account = {
    db.get().accounts.get(id).toRightDisjunction(StateError.AccountNotFound(id))
  }

  def getTransaction(id: TransactionID): StateError \/ Transaction = {
    db.get().transactions.get(id).toRightDisjunction(StateError.TransactionNotFound(id))
  }

  def addUser(user: User): StateError \/ User = {
    db.updateMaybe(_.addUser(user)).map(_ => user)
  }

  def addAccount(account: Account): StateError \/ Account = {
    db.updateMaybe(_.addAccount(account)).map(_ => account)
  }

  def addTransaction(tx: Transaction): StateError \/ Transaction = {
    db.updateMaybe(_.addTransaction(tx)).map(_ => tx)
  }

  def applyTransaction(steps: Seq[TxStep]): StateError \/ Seq[Account] = {
    db.updateMaybe(_.applyTransaction(steps)).map { st =>
      steps.map(s => st.accounts(s.account))
    }
  }

  def matchCurrencies(ids: Seq[(AccountID, AccountID)]): StateError \/ Seq[(Account, Account)]= {
    val accounts = db.get().accounts
    ids.foldLeft(\/-(Nil) : StateError \/ List[(Account, Account)]) {
      case (err@ -\/(_), _) => err
      case (\/-(acc), (aid, bid)) =>
        (accounts.get(aid), accounts.get(bid)) match {
          case (Some(a), Some(b)) if a.currency == b.currency =>
            \/-((a, b) :: acc)
          case (Some(a), Some(b)) =>
            -\/(StateError.CurrencyMismatch(a, b))
          case (None, _) =>
            -\/(StateError.AccountNotFound(aid))
          case (_, None) =>
            -\/(StateError.AccountNotFound(bid))
        }
    }.map(_.reverse)
  }
}

/** Track state as an immutable data structure. */
case class ImState(
    users: Map[UserID, User] = Map()
  , accounts: Map[AccountID, Account] = Map()
  , transactions: Map[TransactionID, Transaction] = Map()
) {

  def addUser(user: User): StateError \/ ImState = {
    users.get(user.id) match {
      case None =>
        \/-(copy(users.updated(user.id, user)))
      case Some(u) =>
        -\/(StateError.UserExists(u.id))
    }
  }

  def addAccount(account: Account): StateError \/ ImState = {
    accounts.get(account.id) match {
      case None =>
        \/-(copy(accounts = accounts.updated(account.id, account)))
      case Some(a) =>
        -\/(StateError.AccountExists(a.id))
    }
  }

  def addTransaction(tx: Transaction): StateError \/ ImState = {
    transactions.get(tx.id) match {
      case None =>
        \/-(copy(transactions = transactions.updated(tx.id, tx)))
      case Some(_) =>
        -\/(StateError.TransactionExists(tx.id))
    }
  }

  def applyTransaction(steps: Seq[TxStep]): StateError \/ ImState = {
    steps.foldLeft(\/-(this): StateError \/ ImState) {
      case (-\/(err: StateError), _) => -\/(err)
      case (\/-(st), step) => st.applyStep(step)
    }
  }

  def applyStep(step: TxStep): StateError \/ ImState = {
    accounts.get(step.account) match {
      case None =>
        -\/(StateError.AccountNotFound(step.account))
      case Some(acc) =>
        val bal = acc.balance + step.amount
        if (bal < 0) {
          -\/(StateError.InsufficientFunds(acc, step))
        } else {
          \/-(copy(accounts = accounts.updated(step.account, acc.copy(balance = bal))))
        }
    }
  }
}

