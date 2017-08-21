
package money.transfers.http

import io.circe.generic.auto._
import io.circe.literal._
import io.circe.syntax._
import io.jvm.uuid.UUID
import money.transfers.FixtureSpec
import money.transfers.data._
import money.transfers.http.ResourceEntity._
import org.http4s.Uri
import org.scalatest.{FlatSpec, MustMatchers}


class ServicesSpec
  extends FlatSpec
     with MustMatchers
     with FixtureSpec {

  val base = Uri.uri("/")

  "Updates" should "return 400 on malformed requests" in withService {
    service =>
      val requests = Seq(
        "transaction" -> json"""{"deposit": null, "transfer": null}"""
      //, "user" -> json"""{"name":null}"""
      //, "account" -> json"""{"name": "test"}"""
      )
      for ((endpoint, payload) <- requests) {
        val response = service.post(base / endpoint, payload)
        response.status.code must equal(400)
      }
  }

  they should "accept and enqueue well-formed requests" in withService {
    service =>
      val uuid = UUID.random
      val requests = Seq(
          ("user", UserRequest("u1").asJson)
        , ("account", AccountRequest(uuid, "a1", "EUR").asJson)
        , ("transaction", TransactionRequest(deposit = Some(DepositRequest(10, uuid, "reference"))).asJson)
        , ("transaction", TransactionRequest(transfer = Some(TransferRequest(100, uuid, uuid))).asJson)
      )
      for (((endpoint, payload), i) <- requests.zipWithIndex) {
        val response = service.post(base / endpoint, payload)
        response.status.code must equal(202)
        service.broker.latestOffset must equal(i)
      }
  }

  "Lookups" should "return 404 on missing resources" in withService {
    service =>
      for (endpoint <- Seq("user", "account", "transaction")) {
        val response = service.get(base / endpoint / UUID.random.toString)
        response.status.code must equal(404)
      }
  }


  they should "return processed entities" in withService {
    service =>

      import Codec._
      import io.circe.java8.time._

      // create user
      val userRequest = UserRequest("test")
      val userResponse = service.post(base / "user", userRequest.asJson)
      userResponse.status.code must equal(202)

      val ev1 = service.processLatest()
      ev1 must not be None
      val userEvent = ev1.get.asInstanceOf[UserCreated]

      val r1 = service.get(userResponse.unsafeLocation)
      r1.status.code must equal(200)
      val user = r1.jsonOf[User]
      user.name must equal(userRequest.name)

      // create account
      val accountRequest = AccountRequest(userEvent.id, "euro account", "EUR")
      val accountResponse = service.post(base / "account", accountRequest.asJson)
      accountResponse.status.code must equal(202)

      val ev2 = service.processLatest()
      ev2 must not be None
      val accountEvent = ev2.get.asInstanceOf[AccountCreated]

      val r2 = service.get(accountResponse.unsafeLocation)
      r2.status.code must equal(200)
      val account = r2.jsonOf[Account]
      account.owner must equal(user)
      account.name must equal(accountRequest.name)
      account.currency.toString must equal(accountRequest.currency)

      // deposit transaction
      val depositRequest: TransactionRequest = TransactionRequest(deposit = Some(DepositRequest(100, account.id, "debit card 0134")))
      val depositResponse = service.post(base / "transaction", depositRequest.asJson)
      depositResponse.status.code must equal(202)

      val ev3 = service.processLatest()
      ev3 must not be None
      val depositEvent = ev3.get.asInstanceOf[TransactionCreated]

      val r3 = service.get(depositResponse.unsafeLocation)
      r3.status.code must equal(200)
      val deposit = r3.jsonOf[Transaction]
      deposit.id must equal(depositEvent.id)

      // transfer transaction
      // Cheat a little by relying on the fact we don't reject same-account transfers
      // to avoid adding more accounts in this test.
      val transferRequest: TransactionRequest = TransactionRequest(transfer = Some(TransferRequest(50, account.id, account.id)))
      val transferResponse = service.post(base / "transaction", transferRequest.asJson)
      transferResponse.status.code must equal(202)

      val ev4 = service.processLatest()
      ev4 must not be None
      val transferEvent = ev4.get.asInstanceOf[TransactionCreated]

      val r4 = service.get(transferResponse.unsafeLocation)
      r4.status.code must equal(200)
      val transfer = r4.jsonOf[Transaction]
      transfer.id must equal(transferEvent.id)
  }
}
