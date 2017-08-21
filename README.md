
# Money Transfers

A simple REST-like API for transferring money between accounts.

## Running

You need [sbt] version 0.13.x (tested with 0.13.16).

Check out the repository and run the following:

```bash
sbt assembly
```

This will create a fat JAR with all the dependencies that you can then
run with the following:

```bash
java -jar target/scala-2.12/transfers-assembly-0.1-SNAPSHOT.jar
```

By default the service will listen on `127.0.0.1` port `8080`, but you
can change that by passing the interface and port as command line
arguments:

```bash
java -jar target/scala-2.12/transfers-assembly-0.1-SNAPSHOT.jar
```

Alternatively you can run with `sbt`:

```bash
sbt run
```

The test suite runs as part of the assembly command, to invoke
separately just run:

```bash
sbt test
```

Once the service is running you can run a simple test script (requires
`bash`, `curl` and [jq]):

```bash
./scripts/smoke-test.sh
```

The output should be

```
account1: 500
account2: 500
```

## API

The API is REST-*like* and asynchronous.

### `POST /v1/user`

Create a new user.

Example:

```json
POST /v1/user
{
  "name": "John Doe"
}
```

```json
202 Accepted
Location: http://127.0.0.1:8080/v1/user/433ba885-a37f-4b6d-ac2a-d5ec6bdca1
{
  "id":"433ba885-a37f-4b6d-ac2a-d5ec6bdca10d"
}
```

### `POST /v1/account`

Create a new account for an existing user.

Example:

```json
POST /v1/account
{
  "name": "Euro account 1",
  "currency": "EUR",
  "user": "433ba885-a37f-4b6d-ac2a-d5ec6bdca10d"
}
```

```json
202 Accepted
Location: http://127.0.0.1:8080/v1/account/d2fc4a53-7754-40a0-aeca-edef443b882c
{
  "id": "d2fc4a53-7754-40a0-aeca-edef443b882c"
}
```

### `POST /v1/transaction`

Submit a new transaction. Two types of transactions are supported:

1.  deposit: track movement of funds where one part of the transaction
    is not managed through our service.
2.  transfer: track movement of funds in a single currency between two
    accounts tracked through our service.

Example:

```json
POST /v1/transaction
{
  "deposit": {
    "amount": 1000,
    "ref": "debit card payment ending 0123",
    "dst": "d2fc4a53-7754-40a0-aeca-edef443b882c"
  }
}
```

```json
202 Accepted
Location: http://127.0.0.1:8080/v1/transaction/d40288bb-5d3c-4ea9-9db9-921d86d6cccb
{
  "id": "d40288bb-5d3c-4ea9-9db9-921d86d6cccb"
}
```

```json
POST /v1/transaction
{
  "transfer": {
    "amount": 500,
    "src": "d2fc4a53-7754-40a0-aeca-edef443b882c",
    "dst": "f3fdda26-2bd1-4862-8330-c34d91470f78"
  }
}
```

```json
202 Accepted
Location: http://127.0.0.1:8080/v1/transaction/ea7a3c04-855d-4604-9991-0683ad408166
{
  "id": "ea7a3c04-855d-4604-9991-0683ad408166"
}
```

### `GET /v1/usr/{id}`

Example:

```json
200 Ok
{
  "id": "433ba885-a37f-4b6d-ac2a-d5ec6bdca10d",
  "name": "John Doe",
  "created": "2017-08-21T15:54:21.904Z"
}
```

### `GET /v1/account/{id}`

Example:

```json
200 Ok
{
  "id": "d2fc4a53-7754-40a0-aeca-edef443b882c",
  "balance": 500,
  "currency": "EUR",
  "name": "Euro account 1",
  "owner": {
    "id": "433ba885-a37f-4b6d-ac2a-d5ec6bdca10d",
    "name": "John Doe",
    "created": "2017-08-21T15:54:21.904Z"
  },
  "created": "2017-08-21T16:00:20.540Z"
}
```

### `GET /v1/transaction/{id}`

Example:

```json
200 Ok
{
  "id": "d40288bb-5d3c-4ea9-9db9-921d86d6cccb",
  "event": {
    "id": "d40288bb-5d3c-4ea9-9db9-921d86d6cccb",
    "received": "2017-08-21T16:07:43.502Z",
    "details": {
      "TxDeposit": {
        "amount": 1000,
        "dst": "d2fc4a53-7754-40a0-aeca-edef443b882c",
        "ref": "debit card ending 0123"
      }
    }
  },
  "created": "2017-08-21T16:07:43.502Z",
  "settled": "2017-08-21T16:07:43.521Z",
  "error": null
}
```

```json
200 Ok
{
  "id": "ea7a3c04-855d-4604-9991-0683ad408166",
  "event": {
    "id": "ea7a3c04-855d-4604-9991-0683ad408166",
    "received": "2017-08-21T16:09:09.715Z",
    "details": {
      "TxTransfer": {
        "amount": 500,
        "src": "d2fc4a53-7754-40a0-aeca-edef443b882c",
        "dst": "f3fdda26-2bd1-4862-8330-c34d91470f78"
      }
    }
  },
  "created": "2017-08-21T16:09:09.715Z",
  "settled": "2017-08-21T16:09:09.770Z",
  "error": null
}
```

```json
{
  "id": "0b8782ed-036c-46b2-a0b8-34193cbf3e19",
  "event": {
    "id": "0b8782ed-036c-46b2-a0b8-34193cbf3e19",
    "received": "2017-08-21T16:17:21.052Z",
    "details": {
      "TxTransfer": {
        "amount": 700,
        "src": "d2fc4a53-7754-40a0-aeca-edef443b882c",
        "dst": "f3fdda26-2bd1-4862-8330-c34d91470f78"
      }
    }
  },
  "created": "2017-08-21T16:17:21.052Z",
  "settled": null,
  "error": "insufficient funds: requested 700 EUR from account d2fc4a53-7754-40a0-aeca-edef443b882c with balance 500"
}
```

### Errors

Error handling is uniform for all endpoints, a single object with an
`error` field containing a `message` and `status` field:

Examples:

```json
404 Not Found
{
  "error": {
    "message": "resource not found 433ba885-a37f-4b6d-ac2a-d5ec6bdca10d",
    "status": 404
  }
}
```

```json
400 Bad Request
{
  "error": {
    "message": "choose one of deposit or transfer",
    "status": 400
  }
}
```

## Design

The service is designed around an event sourcing concept where each
endpoint forwards events into an append-only log via a message broker
after basic validation.

A separate thread picks up pending events for processing and applies
them to the current state which works as a materialised view of the
event log.

The design is somewhat complex for the scope of the application but has
the following benefits:

- Audit logging is built-in and the entire state of the application can
  be reconstructed from just the event log.
- Good separation of concerns, read-only endpoints only depend on a
  read-only view of the state, write endpoints only depend on a client
  that writes to the event queue.
- Naturally allows for an asynchronous API, but can easily be made
  synchronous if necessary.
- Testing the event-processing logic is easy to do in isolation for
  fairly complex test scenarios.
- Event processing can be handled by a separate service if necessary.
- Spikes in volume can be handled smoothly by buffering events without
  rendering the entire service unavailable if transaction processing is
  slow.

The transaction processing data model has been designed so that it can
be easily extended with different transaction types in the future.
Examples of different transaction types are in the source comments.

## Testing

Some basic scaffolding for property-based testing with [scalacheck] is
in place, but it's mostly to showcase the potential and ensure that the
event processing logic does not crash with arbitrary event permutations.

Using the [stateful testing support] of scalacheck should allow for
powerful tests against the API and processing invariants.

The service is lacking in low-level unit tests, though most of the
low-level components border on the trivial with regards to their
implementation. Instead the focus was on higher-level integration-like
tests against the endpoint functions and the processing engine.

## Missing functionality

The service is rough around the edges at places, for example certain
common sanity checks are missing (e.g. `Content-Type:
application/json`), and the built-in fallback components that handle
some error conditions do not return JSON. The error handling logic has
also been kept to a minimum (e.g. no way to distinguish between failed
user or account entities and missing ones).

These changes are all straightforward (albeit time-consuming) to implement.

[sbt]: http://www.scala-sbt.org/
[jq]: https://stedolan.github.io/jq/
[scalacheck]: https://www.scalacheck.org/
[stateful testing support]: https://github.com/rickynils/scalacheck/blob/master/doc/UserGuide.md#stateful-testing
