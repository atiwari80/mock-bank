# Mock Bank App

A small retail-banking web app: sign in as a customer, see your balance, move
money out (transfer), take money out (withdraw), pay bills, apply for a credit
card, and read your statement.

It runs as **five containers** — a React + TypeScript UI, a Spring Boot
middleware holding all the business rules, a Postgres database, and two separate
scoring services (fraud and credit) that the middleware calls out to.

Everything here is fake: fake customers, fake money, fake login. There are no
passwords and no real payment rails.

---

## Running it from a cold machine

Two commands, no setup beforehand:

```bash
docker compose up -d --wait                    # build + start, returns when healthy
docker compose --profile test run --rm smoke   # verify (82 checks)
```

Compose creates its own project network and the services find each other by
service name, so there is nothing to create or configure first.

The first command builds the images on first run — that pulls Maven and Node base
images and takes a few minutes; afterwards it is seconds. `--wait` returns only
once every service reports healthy, so nothing downstream has to poll or sleep.

**Docker is the only thing you need installed.** Java, Maven, Node and Postgres
all live inside containers — the middleware and both scoring services compile
their own jars, and the frontend image runs `tsc` before `vite build` (so a type
error fails the image build). Nothing is built or run against the host, and no
build output is written back into the working tree.

### Ports

| Service | Host | In Docker | Notes |
| --- | --- | --- | --- |
| Frontend | http://localhost:13000 | `frontend:80` | nginx serving the built React app |
| Middleware | http://localhost:18080 | `middleware:8080` | REST API, all business logic |
| Fraud service | http://localhost:18081 | `fraud-service:8081` | `GET /fraud-check`, stateless |
| Credit check | http://localhost:18082 | `credit-check-service:8082` | `GET /credit-check`, stateless |
| Postgres | localhost:15432 | `postgres:5432` | db/user/password all `mockbank` |

Host ports are shifted into the 1xxxx range so this stack can run alongside other
things on the same machine. **Only host access is shifted** — inside Docker the
services listen on their normal ports and address each other by service name.

The UI talks to the middleware through `/api/*` on its own origin — nginx proxies
that to `middleware:8080`, so there is no CORS to configure.

### Resetting to a known state

```bash
docker compose down && docker compose up -d --wait
```

That is the whole reset. Postgres runs without a persistent volume on purpose, so
the database is destroyed and rebuilt: Flyway re-runs every migration on
middleware boot and you are back at the exact starting state, ids and all.
`--wait` means the command returns only when every service is serving. It takes
roughly half a minute.

**Run this between cycles.** This app is stateful on purpose — balances move,
`daily_withdrawn` accumulates, approvals resolve, scheduled payments advance and
credit applications are recorded. A second run against a database left over from
the first will not behave like the first. That is deliberate: proving a suite
needs a clean fixture is part of what this specimen is for.

### Health

Every service that speaks HTTP answers `GET /health` with `200 {"status":"UP"}`:
the middleware, the fraud service and the credit-check service. It needs no
customer header and touches no dependency — no database, no downstream call — so
it answers exactly one question: is this process serving?

All five containers declare a compose healthcheck: the three Spring apps use
their `/health`, nginx is checked by actually fetching `index.html`, and Postgres
uses `pg_isready`. That is what `up --wait` blocks on, so "healthy" means serving
rather than merely started.

### Checking it works

```bash
docker compose --profile test run --rm smoke
```

`smoke` is a small curl container that joins the compose network and calls the
services by name — `middleware:8080`, `fraud-service:8081`,
`credit-check-service:8082`, and the UI through `frontend:80` including its
`/api` proxy. **82 checks** covering health, login, the error contract, all six
flows, the seed fixtures, and that error reasons survive the proxy. It exits
non-zero if anything is off.

The read-only checks run first and the state-changing ones last, so it wants a
freshly-seeded database — run the reset above before a re-run.

Note it is a wiring check, not the test suite — generating the real tests is the
job of the pipeline this app is a specimen for.

---

## Signing in

There is no password. `POST /login {"customerId": 1}` returns the customer if
they exist and 404 if they don't. From that point the UI sends the customer id on
every request as an `X-Customer-Id` header, and the middleware treats that as
"the current user". A missing or unrecognised header gets you
`401 {"reason": "NOT_AUTHENTICATED"}`.

`GET /whoami` echoes back whoever that header resolves to, which is the quickest
way to check a session is being sent correctly.

The session lives in browser memory only — nothing is written to localStorage or
a cookie — so reloading the page signs you out.

### Seed customers

| Id | Name | Account state | What it's for |
| -- | ---- | ------------- | ------------- |
| 1 | Alice Nguyen | $5,000, no hold | The happy path. Enrolled recipients plus an un-enrolled one, 34 transactions spanning May–Aug 2026 across completed / pending / failed, and scheduled payments in all four states. |
| 2 | Brian Kowalski | $3,200, **hold on the account** | Transfers stopped by the hold. |
| 3 | Chloe Ramos | $50, no history | Not-enough-money paths, and an empty statement. |
| 4 | Dev Patel | $25,000, no hold | Well funded — large transfers and anything that needs to interest the fraud service. |
| 5 | Frank Osei | **frozen customer**, $4,000 | A customer whose account is fine but whose profile isn't. |
| 6 | Priya Shah | $6,000, part-way through the day's withdrawals | Hitting the daily cap in a single withdrawal instead of two. |

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
`{reason, message}` shape as a business refusal — there is no second error format
to handle.

---

## The six flows

### 1. Transfer — sending money to a recipient

`POST /transfer {fromAccount, toRecipient, amount}` · `GET /recipients`

You pick one of your saved recipients, enter an amount, and send. Before any
money moves the middleware checks the state of your customer record and account,
whether you have the funds, and whether the recipient is someone you're actually
allowed to send to — recipients have to be enrolled before they can receive
money. Every transfer is also scored by the fraud service, which can let it
through, hold it for review, or stop it.

An optional `X-Ip-Risk` header (0–100, default 0) lets a caller simulate a
riskier origin; it is passed straight through to the fraud service.

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
Approving releases the money and completes it; rejecting voids it. Nothing leaves
the account while it waits.

Exactly which transfers get routed this way is a matter of policy rather than a
single published number — size is the main driver, and other circumstances of the
transfer can factor in. Roughly, transfers in the five-figure range are the ones
that end up in the queue. If you need the precise condition, ask the business
owner rather than inferring it from behaviour.

Once an approval has been decided it can't be decided again — a second attempt
gets `APPROVAL_ALREADY_RESOLVED`. Deciding one that doesn't exist gets
`APPROVAL_NOT_FOUND`, and if the balance has fallen below the parked amount in
the meantime, approving returns `INSUFFICIENT_FUNDS` and leaves it pending.

### 3. Withdraw — taking cash out

`POST /withdraw {account, amount}`

You enter an amount and withdraw it from your account. Two limits apply: no
single withdrawal may exceed **$2,000**, and the running total for the day may
not exceed **$2,000** either. Both are inclusive — exactly $2,000 is fine. A
successful withdrawal debits the balance, adds to the day's running total, and is
recorded in the transaction history.

The checks run in a fixed order and the first one to fail is the one you're told
about: account exists → you have the funds → the single-withdrawal cap → the
daily total. So asking for $2,500 from an account holding $50 tells you about the
money, not the cap.

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
moving, cancelling returns `NOT_CANCELLABLE`, and one that isn't on your account
returns `PAYMENT_NOT_FOUND`. Cancelling deletes the row — the status column has
no `cancelled` value.

### 5. Account & statements

`GET /accounts/me` · `GET /accounts/me/transactions` · `GET /account/{id}` ·
`GET /transactions/{id}?from=&to=&page=&size=`

The dashboard shows the current available balance and whether the account is
under a hold. The statement lists the account's transactions — transfers,
withdrawals and bill payments together — newest first, with amounts, dates and
statuses, including pending and failed ones. It can be narrowed to a date range
and comes back a page at a time (default 10) alongside the totals, so a long
history can be walked through.

The `/accounts/me` pair is the convenience the dashboard uses; the two that take
an explicit id are the same data addressed directly. Asking for an account that
isn't yours returns `ACCOUNT_NOT_FOUND` — the same as one that doesn't exist.

### 6. Credit card application

`POST /credit-card/apply {ssn, requestedLimit}`

You submit a social security number and the limit you want. The middleware
checks the customer is active and the SSN is well formed, then asks the
credit-check service for a bureau report. A report carrying a bankruptcy flag
stops the application; so does a credit standing below what the bank will accept.
Everything that gets through is approved at the limit requested.

Only approvals come back as a `200` — every refusal is a `422` carrying its own
reason, so the response's `status` is always `approved` when you get one.
Applications are recorded either way, approved or declined.

The SSN is **never stored**: only a SHA-256 digest of it is written to the
`credit_applications` row.

Refusals: `CUSTOMER_INACTIVE`, `INVALID_SSN` (it must be exactly 9 digits),
`FRAUD_DECLINE` (a flag on the bureau report), `CREDIT_DECLINE`.

Note the request body also has a `customerId` field. It is **ignored** — identity
always comes from the `X-Customer-Id` header, never from the body.

---

## The scoring services

Two separate Spring Boot apps, neither with a database of its own. Both are black
boxes: how they arrive at a number is deliberately not documented, and is not
exposed through the middleware or the UI. Treat the same input as always giving
the same answer, and mock them rather than reproducing them.

### Fraud — `fraud-service:8081`

The middleware sends the details of a money-out attempt — account, amount,
recipient, whether the recipient is new, an IP risk signal, and how many recent
transfers the account has made — and gets back a score and a decision.

```
GET /fraud-check?account=1&amount=500&recipient=2&ip=0
      &recipientIsNew=false&recentTransferCount=1

→ { "score": 100, "decision": "approve" }
```

The same call is also accepted as a `POST` with a JSON body (`accountId`,
`amount`, `recipientId`, `recipientIsNew`, `ipRisk`, `recentTransferCount`). Both
return the identical answer. `decision` is one of `approve`, `review`, `decline`.

The middleware computes velocity and passes it in rather than the service
tracking it, which is what keeps the service stateless.

### Credit — `credit-check-service:8082`

```
GET /credit-check?ssn=111111111&customerId=1

→ { "score": 750, "report": { "openAccounts": 3, "latePayments": 0, "bankruptcy": false } }
```

---

## Configuration

Every setting is an environment variable with a working default, so the compose
file only overrides what differs.

| Variable | Used by | Default |
| --- | --- | --- |
| `SERVER_PORT` | all three Spring apps | 8080 / 8081 / 8082 |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | middleware | `jdbc:postgresql://localhost:5432/mockbank`, `mockbank`, `mockbank` |
| `FLYWAY_LOCATIONS` | middleware | `classpath:db/migration` |
| `FRAUD_SERVICE_URL` | middleware | `http://localhost:8081` (compose sets `http://fraud-service:8081`) |
| `CREDIT_SERVICE_URL` | middleware | `http://localhost:8082` (compose sets `http://credit-check-service:8082`) |

`FRAUD_SERVICE_URL` is the swap point for the real-vs-mock demonstration: set it
to a stub server and the middleware calls that instead, with no file edit.

---

## Repo layout

```
/middleware             Spring Boot app — REST API, business rules, JPA persistence
/fraud-service          Spring Boot app — the fraud check, standalone and stateless
/credit-check-service   Spring Boot app — the bureau check, standalone and stateless
/frontend               React 18 + TypeScript + Vite UI
/db/migrations          Flyway migrations: V1 schema, V2 seed, V3 seed expansion,
                        V4 credit applications
/smoke                  Containerised smoke test
docker-compose.yml
CLAUDE.md               Working context and rules for this repo
```

### How the middleware is laid out

The schema, the JPA entities and repositories, the error contract, login and the
React shell are **shared** — feature work sits on top of them, and the tables and
their columns are not changed from a feature branch.

```
com.mockbank.persistence   7 entities + repositories (shared)
com.mockbank.common        error contract, exception handler, CustomerContext, /health
com.mockbank.auth          POST /login, GET /whoami
com.mockbank.account       balance + statement
com.mockbank.withdraw      the withdraw rules
com.mockbank.transfer      transfer fan-out, fraud client, approvals
com.mockbank.billpay       scheduled payments
com.mockbank.creditcard    credit card application, credit-check client
```

Each flow keeps its rules in its own package; nothing feature-specific lives in
`common`, `auth` or `persistence`.

## Data model

```
customers            id, name, status
accounts             id, customer_id, balance, hold, daily_withdrawn, status
recipients           id, customer_id, name, enrolled
transactions         id, account_id, type, amount, status, created_at
scheduled_payments   id, account_id, payee, amount, fire_date, status
approvals            id, transfer_ref, amount, status, created_at
credit_applications  id, customer_id, ssn_hash, requested_limit, approved_limit,
                     status, bureau_score, created_at
```

Migrations live in `/db/migrations` and are the single source of truth — the
middleware Docker build copies them onto its classpath and Flyway applies them at
boot. Hibernate is set to `validate`, so the entities are checked against the
migrated schema and never allowed to alter it.
