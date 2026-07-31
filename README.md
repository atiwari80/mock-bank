# Mock Bank App

A small retail-banking web app: sign in as a customer, see your balance, move
money out (transfer), take money out (withdraw), pay bills, and read your
statement. It runs as four containers — a React + TypeScript UI, a Spring Boot
middleware that holds all the business rules, a Postgres database, and a
separate fraud-check service.

Everything here is fake: fake customers, fake money, fake login. There are no
passwords and no real payment rails.

---

## Running it

```bash
docker-compose up --build
```

That brings up all four services. First build pulls Maven/Node images and takes
a few minutes; after that it's quick.

**Docker is the only thing you need installed.** Java, Maven, Node and Postgres
all live inside containers — the middleware and fraud-service images compile
their own jars, and the frontend image runs `tsc` and builds its own bundle
(so a type error fails the image build). Nothing is built or run against the
host, and no build output is written back into the working tree.

| Service       | URL                     | Notes                                        |
| ------------- | ----------------------- | -------------------------------------------- |
| Frontend      | http://localhost:3000   | nginx serving the built React app             |
| Middleware    | http://localhost:8080   | REST API, all business logic                  |
| Fraud service | http://localhost:8081   | `POST /fraud-check`, stateless                |
| Postgres      | localhost:5432          | db `mockbank`, user/password `mockbank`       |

The UI talks to the middleware through `/api/*` on its own origin — nginx proxies
that to `middleware:8080`, so there is no CORS setup to worry about.

**Resetting the data.** Postgres runs without a persistent volume on purpose.
`docker-compose down && docker-compose up` gives you a brand new database:
Flyway re-runs `V1__schema.sql` and `V2__seed.sql` on middleware boot, and you're
back to the exact starting state.

### Checking it works

```bash
docker-compose --profile test run --rm smoke
```

`smoke` is a small curl container that joins the compose network and calls the
services by name — `middleware:8080`, `fraud-service:8081`, and the UI through
`frontend:80` including its `/api` proxy. It asserts the wiring and the error
contract (login, 401 / 404 / 400 / 405 shapes, the fraud stub, SPA deep links,
and that error reasons survive the proxy). It exits non-zero if anything is off.

It deliberately does not assert feature rules — those belong to whoever owns the
feature.

---

## Signing in

There is no password. `POST /login {"customerId": 1}` returns the customer if
they exist and 404 if they don't. From that point the UI sends the customer id
on every request as an `X-Customer-Id` header, and the middleware treats that as
"the current user". A missing or unrecognised header gets you
`401 {"reason": "NOT_AUTHENTICATED"}`.

`GET /whoami` echoes back whoever that header resolves to, which is the quickest
way to check a session is being sent correctly.

The session lives in browser memory only — nothing is written to localStorage or
a cookie — so reloading the page signs you out.

### Seed customers

| Id | Name           | Account state                    | What it's for                                       |
| -- | -------------- | -------------------------------- | --------------------------------------------------- |
| 1  | Alice Nguyen   | $5,000, no hold                  | The happy path. Has enrolled recipients, an un-enrolled one, and a short transaction history across completed / pending / failed. |
| 2  | Brian Kowalski | $3,200, **hold on the account**  | Money-out attempts that get stopped by the hold.     |
| 3  | Chloe Ramos    | $50                              | Not-enough-money paths.                              |
| 4  | Dev Patel      | $25,000, no hold                 | Well funded — large transfers and anything that needs to interest the fraud service. |

---

## How errors work

Every business refusal comes back as `422` with a body of exactly this shape:

```json
{ "reason": "INSUFFICIENT_FUNDS", "message": "Your available balance is $50.00." }
```

`reason` is a stable code; `message` is human text the UI shows as-is. Reasons are
specific — you always learn *which* rule stopped you, never just "declined". The
UI shows both, so a failure on screen tells you the same thing the API told the
caller.

Status codes: `422` a rule refused it · `401` not signed in · `404` no such thing
· `400` malformed request · `405` wrong method. Everything goes through one
`@RestControllerAdvice`, so an unmapped URL or a bad body comes back in the same
`{reason, message}` shape as a business refusal — there is no second error
format to handle.

---

## The five flows

### 1. Transfer — sending money to a recipient

You pick one of your saved recipients, enter an amount, and send. Before any
money moves the middleware checks the state of your customer record and account,
whether you have the funds, and whether the recipient is someone you're actually
allowed to send to — recipients have to be enrolled before they can receive
money. Every transfer is also scored by the fraud service, which can let it
through, hold it for review, or stop it.

A completed transfer debits the account and lands in the transaction history.

Refusals you can hit: `CUSTOMER_INACTIVE`, `ACCOUNT_NOT_FOUND`,
`INSUFFICIENT_FUNDS`, `ACCOUNT_HOLD`, `RECIPIENT_NOT_ENROLLED`, `FRAUD_DECLINE`,
`FRAUD_REVIEW`.

### 2. Large-transfer approval

Transfers of an unusually large size don't complete straight away. Instead of
debiting the account, the middleware parks the transfer and raises an approval
request; the transfer stays pending until someone approves or rejects it. The
customer sees that their transfer is awaiting approval rather than a refusal.

Exactly which transfers get routed this way is a matter of policy rather than a
single published number — size is the main driver, and other circumstances of
the transfer can factor in. Roughly, transfers in the five-figure range are the
ones that end up in the queue. If you need the precise condition, ask the
business owner rather than inferring it from behaviour.

Once an approval has been decided it can't be decided again — a second attempt
gets `APPROVAL_ALREADY_RESOLVED`.

### 3. Withdraw — taking cash out

You enter an amount and withdraw it from your account. Withdrawals are subject
to limits: there's a cap on a single withdrawal, and a separate cap on how much
you can take out across a day (the running total is tracked per account). A
successful withdrawal debits the balance, adds to the day's running total, and is
recorded in the transaction history.

Refusals: `ACCOUNT_NOT_FOUND`, `INSUFFICIENT_FUNDS`, `EXCEEDS_TXN_LIMIT`,
`EXCEEDS_DAILY_LIMIT`.

### 4. Bill Pay — scheduled payments

You schedule a payment to a payee for a future date. Scheduled payments move
through their lifecycle — `scheduled` → `pending` → `paid` or `failed` — and you
can see what's coming up. A scheduled payment can be cancelled while it's still
genuinely cancellable; once it's moved past that point, cancelling returns
`NOT_CANCELLABLE`.

### 5. Account & statements

The dashboard shows the current available balance and whether the account is
under a hold. The statement view lists the account's transactions — transfers,
withdrawals and bill payments together — with their amounts, dates and statuses,
including the ones that are pending or failed.

---

## The fraud service

A separate Spring Boot app on port 8081, with no database of its own. The
middleware posts it the details of a money-out attempt — account, amount,
recipient, whether the recipient is new, an IP risk signal, and how many recent
transfers the account has made — and gets back a score and a decision.

```
POST /fraud-check
{ "accountId": 1, "amount": 500.00, "recipientId": 2,
  "recipientIsNew": false, "ipRisk": 0, "recentTransferCount": 1 }

→ { "score": 0, "decision": "approve" }
```

How the score is arrived at is deliberately not documented and is not exposed
through the middleware or the UI. Treat it as a black box: the same request
always gets the same answer, but the weighting behind it isn't something callers
get to see. The middleware passes velocity in rather than the service tracking
it, which is what keeps the service stateless.

---

## Repo layout

```
/middleware      Spring Boot app — REST API, business rules, JPA persistence
/fraud-service   Spring Boot app — the fraud check, standalone and stateless
/frontend        React 18 + TypeScript + Vite UI
/db/migrations   Flyway migrations: V1 schema, V2 seed
/smoke           Containerised smoke test for the shared foundation
docker-compose.yml
CLAUDE.md        Working context and rules for this repo
```

### Shared foundation vs. feature code

The schema, the JPA entities and repositories, the error contract, login, the
React shell and the dashboard are **shared and frozen** — they're built once and
the feature work sits on top of them. In particular, the six tables and their
columns are not to be changed from a feature branch.

Two feature areas are then built independently on that base:

- **Money Out** — transfer, large-transfer approval, the fraud service scoring.
- **Account Ops** — withdraw, bill pay, account and statement views.

The middleware currently ships the shared layer only: `com.mockbank.persistence`
(entities + repositories), `com.mockbank.common` (error contract, exception
handler, `CustomerContext`) and `com.mockbank.auth` (login and `/whoami`).
Feature packages are added by whoever owns that feature.

## Data model

```
customers          id, name, status
accounts           id, customer_id, balance, hold, daily_withdrawn, status
recipients         id, customer_id, name, enrolled
transactions       id, account_id, type, amount, status, created_at
scheduled_payments id, account_id, payee, amount, fire_date, status
approvals          id, transfer_ref, amount, status, created_at
```

Migrations live in `/db/migrations` and are the single source of truth — the
middleware Docker build copies them onto its classpath and Flyway applies them at
boot. Hibernate is set to `validate`, so the entities are checked against the
migrated schema and never allowed to alter it.
