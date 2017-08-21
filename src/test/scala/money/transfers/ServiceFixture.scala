
package money.transfers

import io.circe.Json
import money.transfers.http.Services
import money.transfers.process.EventProcessor
import money.transfers.state.{EventBroker, State}
import org.http4s.headers.Location
import org.http4s.{Method, Request, Response, Uri}


class ServiceFixture (
    val state: State = new State
  , val broker: EventBroker = new EventBroker()
) {
  val service = new Services(Uri.uri("/"))
  val lookup = service.lookupService(state)
  val update = service.updateService(broker)

  val processor = new EventProcessor(state)

  def get(uri: Uri): Response = {
    val request = Request(Method.GET, uri)
    lookup.run(request).unsafePerformSync
  }

  def post(uri: Uri, resource: Json): Response = {
    import org.http4s.circe._
    val request = Request(Method.POST, uri).withBody(resource)
    request.flatMap(update.run).unsafePerformSync
  }

  def processLatest() = {
    broker.get(broker.latestOffset()).map { event =>
      processor.process(event)
      event
    }
  }
}

trait FixtureSpec {
  def withService(test: ServiceFixture => Any): Any = {
    test(new ServiceFixture())
  }

  implicit class RichResponse(obj: Response) {
    def jsonOf[T: io.circe.Decoder] = {
      obj.as(org.http4s.circe.jsonOf[T]).unsafePerformSync
    }

    def unsafeLocation: Uri = {
      obj.headers.get(Location).map(_.uri).get
    }
  }
}
