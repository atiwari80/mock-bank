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
`frontend:80` including its `/api` proxy. **62 checks** covering login, the error
contract, all five flows (including both fraud branches, the approval lifecycle
and the bill-pay state machine), the seed fixtures, and that error reasons
survive the proxy. It exits non-zero if anything is off.

The read-only checks run first and the state-changing ones last, so it wants a
freshly-seeded database: `docker-compose down && docker-compose up -d` before a
re-run.

Note it is a wiring check, not the test suite — generating the real tests is the
job of the pipeline this app is a specimen for.

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
| 1  | Alice Nguyen   | $5,000, no hold                  | The happy path. Enrolled recipients plus an un-enrolled one, 34 transactions spanning May–Aug 2026 across completed / pending / failed, and scheduled payments in all four states. |
| 2  | Brian Kowalski | $3,200, **hold on the account**  | Transfers stopped by the hold.                       |
| 3  | Chloe Ramos    | $50, no history                  | Not-enough-money paths, and an empty statement.      |
| 4  | Dev Patel      | $25,000, no hold                 | Well funded — large transfers and anything that needs to interest the fraud service. |
| 5  | Frank Osei     | **frozen customer**, $4,000      | A customer whose account is fine but whose profile isn't. |
| 6  | Priya Shah     | $6,000, part-way through the day's withdrawals | Hitting the daily cap in a single withdrawal instead of two. |

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

`POST /transfer {fromAccount, toRecipient, amount}` · `GET /recipients`

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

`GET /approvals?status=pending` · `POST /approvals/{id}/approve` ·
`POST /approvals/{id}/reject`

Transfers of an unusually large size don't complete straight away. Instead of
debiting the account, the middleware parks the transfer and raises an approval
request; the transfer stays pending until someone approves or rejects it. The
customer sees that their transfer is awaiting approval rather than a refusal.
Approving releases the money and completes it; rejecting voids it. Nothing
leaves the account while it waits.

Exactly which transfers get routed this way is a matter of policy rather than a
single published number — size is the main driver, and other circumstances of
the transfer can factor in. Roughly, transfers in the five-figure range are the
ones that end up in the queue. If you need the precise condition, ask the
business owner rather than inferring it from behaviour.

Once an approval has been decided it can't be decided again — a second attempt
gets `APPROVAL_ALREADY_RESOLVED`.

### 3. Withdraw — taking cash out

`POST /withdraw {account, amount}`

You enter an amount and withdraw it from your account. Two limits apply: no
single withdrawal may exceed **$2,000**, and the running total for the day may
not exceed **$2,000** either. Both are inclusive — exactly $2,000 is fine.
A successful withdrawal debits the balance, adds to the day's running total, and
is recorded in the transaction history.

The checks run in a fixed order and the first one to fail is the one you're told
about: account exists → you have the funds → the single-withdrawal cap → the
daily total. So asking for $2,500 from an account holding $50 tells you about
the money, not the cap.

A hold on the account does *not* stop a withdrawal — holds only stop transfers.

Refusals: `ACCOUNT_NOT_FOUND`, `INSUFFICIENT_FUNDS`, `EXCEEDS_TXN_LIMIT`,
`EXCEEDS_DAILY_LIMIT`.

### 4. Bill Pay — scheduled payments

`POST /schedule-payment {payee, amount, date}` · `GET /scheduled-payments` ·
`POST /scheduled-payments/{id}/cancel` · `POST /scheduled-payments/run {asOfDate}`

You schedule a payment to a payee for a future date. Payments move through
`scheduled` → `pending` → `paid`, or `failed` if the money isn't there when they
fire.

Nothing fires on a timer. `run` advances everything due as at a date you choose
by exactly one step, which means a caller decides when "the date arrives" and can
watch a payment sit in `pending` before it settles.

A payment can be cancelled only while it's still `scheduled`; once it has started
moving, cancelling returns `NOT_CANCELLABLE`.

### 5. Account & statements

`GET /account/{id}` · `GET /transactions/{id}?from=&to=&page=&size=`

The dashboard shows the current available balance and whether the account is
under a hold. The statement lists the account's transactions — transfers,
withdrawals and bill payments together — newest first, with amounts, dates and
statuses, including pending and failed ones. It can be narrowed to a date range
and comes back a page at a time (default 10) alongside the totals, so a long
history can be walked through.

Asking for an account that isn't yours returns `ACCOUNT_NOT_FOUND` — the same as
one that doesn't exist.

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

### How the middleware is laid out

The schema, the JPA entities and repositories, the error contract, login and the
React shell are **shared and frozen** — the feature work sits on top of them, and
the six tables and their columns are not changed from a feature branch.

```
com.mockbank.persistence   6 entities + repositories (shared)
com.mockbank.common        error contract, exception handler, CustomerContext
com.mockbank.auth          POST /login, GET /whoami
com.mockbank.account       balance + statement
com.mockbank.withdraw      the withdraw rules
com.mockbank.transfer      transfer fan-out, fraud client, approvals
com.mockbank.billpay       scheduled payments
```

Each flow keeps its rules in its own package; nothing feature-specific lives in
`common`, `auth` or `persistence`.

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
