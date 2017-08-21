
package money.transfers.process

import java.time.Instant
import java.util.Currency

import io.jvm.uuid.UUID
import money.transfers.data._
import money.transfers.state.State
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, MustMatchers}


class ProcessSpec
extends FlatSpec
  with MustMatchers
  with PropertyChecks {

  "Event processing" should "not crash" in {
    // Run different processing permutations.
    // More advanced tests can check processing invariants are met.
    forAll(Generators.eventGen(7, 7, 13)) { events =>
      val processor = new EventProcessor(new State)
      for (ev <- events) {
        System.err.print(s"  processing $ev")
        processor.process(ev).map(_ => System.err.println(" ok!")).leftMap { err =>
          System.err.println(s" error $err")
        }
      }
    }
  }
}

object Generators {

  /** Generate sequences of consistent domain events. */
  def eventGen(numUsers: Int, numAccounts: Int, numTx: Int): Gen[List[DomainEvent]] = {
    for {
      userEv <- Gen.listOfN(numUsers, users)
      accountEv <- Gen.listOfN(numAccounts, accounts(Gen.oneOf(userEv)))
      txEv <- Gen.listOfN(numTx, transactions(Gen.oneOf(accountEv), Gen.choose(1L, 100L).map(BigInt(_))))
    } yield userEv ++ accountEv ++ txEv
  }

  def users: Gen[UserCreated] = {
    for {
      id <- uuids
      name <- names()
      created <- instants
    } yield UserCreated(id, name, created)
  }

  def accounts(users: Gen[UserCreated]): Gen[AccountCreated] = {
    for {
      id <- uuids
      name <- names()
      created <- instants
      currency <- currencies
      user <- users
    } yield AccountCreated(id, currency, name, created, user.id)
  }

  def transactions(accounts: Gen[AccountCreated], amounts: Gen[BigInt]) = {
    for {
      id <- uuids
      details <- Gen.oneOf(deposits(accounts, amounts), transfers(accounts, amounts))
      received <- instants
    } yield TransactionCreated(id, received, details)
  }

  def deposits(accounts: Gen[AccountCreated], amounts: Gen[BigInt]) = {
    for {
      amount <- amounts
      dst <- accounts
      ref <- names()
    } yield TxDeposit(amount, dst.id, ref)
  }

  def transfers(accounts: Gen[AccountCreated], amounts: Gen[BigInt]) = {
    for {
      amount <- amounts
      src <- accounts
      dst <- accounts
      if src.id != dst.id
    } yield TxTransfer(amount, src.id, dst.id)
  }

  def names(max: Int = 20): Gen[String] = {
    for {
      size <- Gen.choose(0, max)
      chars <- Gen.listOfN(size, Gen.alphaNumChar)
    } yield chars.mkString("")
  }

  def currencies: Gen[Currency] = {
    for {e <- Gen.oneOf("EUR", "GBP", "USD")} yield {Currency.getInstance(e)}
  }

  def uuids: Gen[UUID] = {
    Gen.uuid
  }

  // https://github.com/gloriousfutureio/scalacheck-ops
  def instants: Gen[Instant] = {
    for {
      millis <- Gen.choose(0L, Instant.MAX.getEpochSecond)
      nanos <- Gen.choose(0, Instant.MAX.getNano)
    } yield Instant.ofEpochMilli(millis).plusNanos(nanos)
  }
}
