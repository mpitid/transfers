
package money.transfers

import java.util.concurrent.{Executors, TimeUnit}

import money.transfers.process.{EventProcessor, EventProcessorRunnable}
import money.transfers.state.{EventBroker, State}
import org.http4s.Uri
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerApp}

import scalaz.concurrent.Task

object service extends ServerApp {

  val vsn = "v1"

  override def server(args: List[String]): Task[Server] = {
    val (host, port) = args match {
      case h :: p :: Nil => (h, p.toInt)
      case _ => ("127.0.0.1", 8080)
    }

    val base = Uri.unsafeFromString(s"http://$host:$port")
    val clock = java.time.Clock.systemUTC()

    val state = new State
    val broker = new EventBroker
    val processor = new EventProcessor(state, clock)
    val services = new http.Services(base, clock)

    val exec = Executors.newScheduledThreadPool(1)
    exec.scheduleWithFixedDelay(
      new EventProcessorRunnable(broker, processor),
      100, 100, TimeUnit.MILLISECONDS)

    BlazeBuilder
        .bindHttp(port, host)
        .mountService(services.lookupService(state), "/" + vsn)
        .mountService(services.updateService(broker), "/" + vsn)
        .start
  }
}
