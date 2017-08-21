
package money.transfers.state

import java.util.concurrent.atomic.AtomicReference

import money.transfers.data.{DomainEvent, Offset}
import money.transfers.util.RichAtomicReference


trait EventProducer {
  def add(event: DomainEvent): Offset
}

trait EventConsumer {
  def get(offset: Offset): Option[DomainEvent]
  def latestOffset(): Offset
}


class EventBroker(
  init: Option[Seq[DomainEvent]] = None
) extends EventProducer
     with EventConsumer {

  protected val events = new AtomicReference(init.getOrElse(Vector()))

  def add(event: DomainEvent) = {
    events.update(_ :+ event).length
  }

  def get(offset: Offset) = {
    val seq = events.get()
    if (offset < 0 || offset >= seq.length) {
      None
    } else {
      Some(seq(offset))
    }
  }

  def latestOffset() = {
    events.get().length-1
  }
}


