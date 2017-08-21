
package money.transfers.http

import java.util.Currency

import io.circe.generic.auto._
import io.circe.syntax._
import io.jvm.uuid.UUID
import money.transfers.data._
import money.transfers.http.ResourceEntity._
import money.transfers.state.{EventProducer, StateError, StateReadable}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.headers.Location

import scala.util.Try
import scalaz.concurrent.Task
import scalaz.{-\/, \/, \/-}


class Services(
    base: Uri
  , clock: java.time.Clock = java.time.Clock.systemUTC()
  , rng: scala.util.Random = new scala.util.Random
) {

  private[this] val log = org.log4s.getLogger

  import Codec._

  def lookupService(state: StateReadable) = HttpService {
    case GET -> Root / "user" / UUIDVar(uid) =>
      for {
        result <- Task(state.getUser(uid).leftMap(apiError))
        response <- handleLookup(result)
      } yield response

    case GET -> Root / "account" / UUIDVar(aid) =>
      for {
        result <- Task(state.getAccount(aid).leftMap(apiError))
        response <- handleLookup(result)
      } yield response

    case GET -> Root / "transaction" / UUIDVar(tid) =>
      for {
        result <- Task(state.getTransaction(tid).leftMap(apiError))
        response <- handleLookup(result)
      } yield response
  }

  def updateService(sink: EventProducer) = HttpService {
    case request @ POST -> Root / "user" =>
      for {
        body <- request.as(jsonOf[UserRequest])
        event <- Task(userEvent(body))
        response <- handleUpdate(sink, request.uri, event)
      } yield response

    case request @ POST -> Root / "account" =>
      for {
        body <- request.as(jsonOf[AccountRequest])
        event <- Task(accountEvent(body))
        response <- handleUpdate(sink, request.uri, event)
      } yield response

    case request @ POST -> Root / "transaction" =>
      for {
        body <- request.as(jsonOf[TransactionRequest])
        event <- Task(transactionEvent(body))
        response <- handleUpdate(sink, request.uri, event)
      } yield response
  }


  def handleLookup[A:io.circe.Encoder](outcome: ApiError \/ A): Task[Response] = {
    outcome.leftMap(handleError).map { resource =>
      log.debug(s"returning $resource")
      Ok(resource.asJson)
    }.merge
  }

  def handleUpdate(sink: EventProducer, endpoint: Uri, outcome: ApiError \/ DomainEvent): Task[Response] = {
    outcome.leftMap(handleError).map { event =>
      Task {
        log.debug(s"enqueueing $event")
        sink.add(event)
      }.flatMap { offset =>
        log.debug(s"enqueued event $event at offset $offset")
        val id = event.id.toString
        Accepted()
          .putHeaders(Location(base.withPath(endpoint.toString) / id))
          .withBody(ResourceId(id).asJson)
      }
    }.merge
  }

  def handleError(error: ApiError): Task[Response] = {
    error match {
      case err @ ApiError.InternalError(_, cause) =>
        log.warn(cause)(err.message)
      case _ =>
        log.debug(s"error $error")
    }
    Response(status = Status.fromInt(error.status).valueOr(throw _)).withBody(error.asJson)
  }

  def apiError(err: StateError): ApiError = {
    import money.transfers.state.StateError._
    err match {
      case UserNotFound(id) => ApiError.ResourceNotFound(id.toString)
      case AccountNotFound(id) => ApiError.ResourceNotFound(id.toString)
      case TransactionNotFound(id) => ApiError.ResourceNotFound(id.toString)
      case _ => ApiError.InternalError(rng.nextLong(), err)
    }
  }

  def userEvent(req: UserRequest): ApiError \/ UserCreated = {
    \/-(UserCreated(UUID.random, req.name, clock.instant()))
  }

  def accountEvent(req: AccountRequest): ApiError \/ AccountCreated = {
    Try(Currency.getInstance(req.currency)) match {
      case scala.util.Success(currency) =>
        \/-(AccountCreated(UUID.random, currency, req.name, clock.instant(), req.user))
      case scala.util.Failure(_) =>
        -\/(ApiError.UnsupportedCurrency(req.currency))
    }
  }

  def transactionEvent(req: TransactionRequest): ApiError \/ TransactionCreated = {
    (req.deposit, req.transfer) match {
      case (Some(d), None) =>
        \/-(TransactionCreated(UUID.random, clock.instant(), TxDeposit(d.amount, d.dst, d.ref)))
      case (None, Some(t)) =>
        \/-(TransactionCreated(UUID.random, clock.instant(), TxTransfer(t.amount, t.src, t.dst)))
      case (_, _) =>
        -\/(ApiError.MalformedTransaction("choose one of deposit or transfer"))
    }
  }
}


/** Extend automatic codec derivation with some custom instances. */
object Codec {
  import io.circe.{Decoder, Encoder}
  implicit val encodeCurrency: Encoder[Currency] = Encoder.encodeString.contramap(_.getCurrencyCode())
  implicit val decodeCurrency: Decoder[Currency] = Decoder.decodeString.map(Currency.getInstance)

  import io.circe.literal._
  implicit val encodeApiError: Encoder[ApiError] = Encoder.instance { err =>
    json"""{"error": {"message": ${err.message}, "status": ${err.status}}}"""
  }

  // XXX work around unused import warning for io.circe.java8.time._
  implicit val encodeInstant: Encoder[java.time.Instant] = io.circe.java8.time.encodeInstant
}


/** Extract UUID from path parameters. */
object UUIDVar {
  def unapply(str: String): Option[UUID] = {
    Try(UUID(str)).toOption
  }
}

