
package money.transfers.process

import money.transfers.data._
import money.transfers.state.{EventBroker, State, StateError}

import scalaz.\/


/** Process a single event to update the state. */
class EventProcessor(
    state: State
  , clock: java.time.Clock = java.time.Clock.systemUTC()
) {

  def process(event: DomainEvent): StateError \/ State = {
    event match {
      case UserCreated(id, name, created) =>
        state.addUser(User(id, name, created)).map(_ => state)

      case AccountCreated(id, currency, name, created, userId) =>
        for {
          user <- state.getUser(userId)
          account <- state.addAccount(Account(id, 0, currency, name, user, created))
        } yield state

      case tx @ TransactionCreated(id, received, details) =>
        val outcome =
          for {
            _ <- state.matchCurrencies(tx.details.matchCurrencies)
            accounts <- state.applyTransaction(tx.steps)
          } yield state
        val transaction =
          outcome.map { _ =>
            Transaction(tx.id, tx, tx.received, Some(clock.instant()))
          }.leftMap { err =>
            Transaction(tx.id, tx, tx.received, error = StateError.transactionFailed(err))
          }.merge
        val outcome2 = state.addTransaction(transaction).map(_ => state)
        if (outcome.isLeft) { // first error takes precedence
          outcome
        } else {
          outcome2
        }
    }
  }
}


/** Process events from the queue in batched intervals. */
class EventProcessorRunnable(
  broker: EventBroker
  , proc: EventProcessor
  , batch: Int = 10
  , startOffset: Offset = 0
) extends Runnable {

  private[this] val log = org.log4s.getLogger(this.getClass)
  protected var offset = startOffset

  def run() = {
    log.trace(s"processing offset $offset")
    for (i <- 0.until(batch)) {
      broker.get(offset).foreach {
        event =>
          log.info(s"processing event $event")
          proc.process(event).leftMap { err =>
            log.warn(s"error processing event $event: $err")
          }
          offset += 1
      }
    }
  }
}

