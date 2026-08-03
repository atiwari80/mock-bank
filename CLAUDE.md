# Mock Bank App — Project Context for Claude Code

## What this is
A deliberately-hard **test specimen** for an AI test-generation pipeline. NOT a
production product. It is a mock retail-banking app. Its job is to contain
realistic, hard testing scenarios ON PURPOSE so a separate testing pipeline
(built later, in another repo) can prove it handles them.

**Critical mindset:** difficulty is a feature. Do NOT simplify, sand down, or
"clean up" the hard or ambiguous parts. If something looks under-specified,
that may be intentional — check this file before resolving it.

## Architecture
Four containers via docker-compose:
- React UI (nginx) — Playwright will test this later
- Middleware (Spring Boot, Java) — all business logic; REST Assured tests this
- Postgres — REAL database, containerized so it's disposable/resettable. NOT mocked.
- Fraud-check service — SEPARATE Spring Boot service, opaque scoring, stateless.

Repo layout:
- `/middleware`   Spring Boot main app
- `/fraud-service` Spring Boot fraud-check service (separate app)
- `/frontend`     React app
- `/db`           Flyway migrations + seed
- `/smoke`        Containerised smoke test for the shared foundation
- `CLAUDE.md`, `README.md`

## Current state (read this before starting work)
**Step 0 is DONE and FROZEN.** The shared foundation exists and is verified:
schema + seed, all six JPA entities and repositories, the error contract,
`CustomerContext`, fake login, the React shell, the fraud-service skeleton,
docker-compose, and the smoke test.

What is in the middleware today:
- `com.mockbank.persistence` — 6 entities + 6 Spring Data repositories (shared)
- `com.mockbank.common` — `ErrorResponse`, `BusinessException`,
  `NotFoundException`, `NotAuthenticatedException`, `GlobalExceptionHandler`,
  `CustomerContext`
- `com.mockbank.auth` — `LoginController` (`POST /login`),
  `SessionController` (`GET /whoami`)
- `com.mockbank.account` — account summary and statement (paging + date filter)
- `com.mockbank.withdraw` — the withdraw contract below
- `com.mockbank.transfer` — transfer fan-out, the fraud client, and approvals
- `com.mockbank.billpay` — scheduled payments and the state machine

Feature logic stays in its own package. Do not put it in `common`, `auth`, or
`persistence`.

### Feature progress — all five flows are built
| Flow | Status |
| --- | --- |
| Account & statement view | DONE — paging + date filter, API + UI |
| Withdraw | DONE — full precedence chain, API + UI |
| Transfer (fan-out) | DONE — 7 distinct outcomes, API + UI |
| Large-transfer approval | DONE — park / approve / reject, ambiguity intact |
| Bill Pay | DONE — state machine + test-driven firing, API + UI |
| Fraud scoring | DONE — opaque 0–1000 score, four factors |

Every screen is real; there is no `PlaceholderScreen` any more. The smoke suite
covers all five flows (62 checks).

### Endpoint inventory
```
POST /login                              GET  /whoami
GET  /accounts/me                        GET  /accounts/me/transactions
GET  /account/{id}                       GET  /transactions/{id}?from=&to=&page=&size=
POST /withdraw                           POST /transfer            GET /recipients
GET  /approvals?status=                  POST /approvals/{id}/approve|reject
POST /schedule-payment                   GET  /scheduled-payments
POST /scheduled-payments/{id}/cancel     POST /scheduled-payments/run
```

## The two verticals (parallel ownership)
- Person A "Money Out": Transfer (+ fan-out), Large-transfer approval, Fraud-check service.
- Person B "Account Ops": Withdraw, Bill Pay, Account/Statement view.
Shared foundation (schema, common error handling, React shell, login, dashboard)
is built ONCE and frozen. Feature code is owned solo. NEVER edit the schema solo.

## Database schema (FROZEN — do not alter columns)
customers(id, name, status['active'|'frozen'])
accounts(id, customer_id, balance NUMERIC(14,2), hold bool, daily_withdrawn NUMERIC(14,2), status)
recipients(id, customer_id, name, enrolled bool)
transactions(id, account_id, type['transfer'|'withdraw'|'billpay'], amount, status['completed'|'pending'|'failed'], created_at)
scheduled_payments(id, account_id, payee, amount, fire_date, status['scheduled'|'pending'|'paid'|'failed'])
approvals(id, transfer_ref, amount, status['pending'|'approved'|'rejected'], created_at)

Migrations live in `/db/migrations` (`V1__schema.sql`, `V2__seed.sql`) and are
the single source of truth — the middleware Docker build copies them onto its
classpath. Hibernate runs `ddl-auto: validate`, so entities are checked against
the migrated schema and can never alter it. Seeded ids are 1..4; identity
sequences restart at 100 so application inserts never collide with the seed.
Do NOT add a V3 migration without agreement from both vertical owners.

## Iron rules (apply to ALL code)
1. **Distinct error reasons.** Every business failure returns
   `422 { "reason": "<STABLE_CODE>", "message": "<human text>" }` with a SPECIFIC
   reason code. NEVER a generic "denied"/"failed". The UI shows the specific
   message too. Tests assert on `reason`. This is the most important rule.
   Throw `BusinessException(reason, message)` — `GlobalExceptionHandler` does
   the rest. Never build an error body by hand.
2. **Fake login.** No passwords/sessions/tokens. `POST /login {customerId}`.
   UI then sends `X-Customer-Id: <id>` header on every request. Middleware reads
   it for "current user". Missing/invalid → 401 {reason:"NOT_AUTHENTICATED"}.
   Inject `CustomerContext` and call `requireCustomerId()` / `requireCustomer()`.
   Note it does NOT check customer status — "frozen customer" is a feature rule
   with its own reason code (`CUSTOMER_INACTIVE`), not an auth failure.
3. **Real DB, not mocked.** Postgres in a container. Reset by reseed/recreate.
4. **Fraud service is a black box.** Opaque weighted score, internal logic hidden
   from UI and API docs. Stateless. Middleware passes velocity in.
5. **Docker only.** Every build and test runs in Docker. Do NOT run `mvn`, `npm`,
   `java`, `psql`, or host `curl` against this repo, and do NOT bind-mount source
   into a build container (it writes `target/`, `node_modules/` back into the
   tree). Build with `docker compose build`; verify with
   `docker compose --profile test run --rm smoke`.

## Error codes (the contract)
Transfer:  CUSTOMER_INACTIVE, ACCOUNT_NOT_FOUND, INSUFFICIENT_FUNDS, ACCOUNT_HOLD,
           FRAUD_DECLINE, FRAUD_REVIEW, RECIPIENT_NOT_ENROLLED
Approval:  APPROVAL_ALREADY_RESOLVED
Withdraw:  ACCOUNT_NOT_FOUND, INSUFFICIENT_FUNDS, EXCEEDS_TXN_LIMIT, EXCEEDS_DAILY_LIMIT
Bill Pay:  NOT_CANCELLABLE
Auth:      NOT_AUTHENTICATED

Shared/infrastructure codes already emitted by the foundation (do not reuse
these for business outcomes): CUSTOMER_NOT_FOUND (404 from `/login`), NOT_FOUND,
BAD_REQUEST, METHOD_NOT_ALLOWED, INTERNAL_ERROR.

## Withdraw contract (SETTLED — from the POC spec, implement exactly)
- Per-transaction cap **$2,000**. Cumulative daily cap **$2,000**.
- Boundaries are **inclusive**: `amount <= 2000` passes, `2000.01` fails;
  `daily_withdrawn + amount <= 2000` passes.
- **Check order IS the precedence.** First failure wins and is the reason the
  tests assert on:
  1. `ACCOUNT_NOT_FOUND` — account exists
  2. `INSUFFICIENT_FUNDS` — balance >= amount
  3. `EXCEEDS_TXN_LIMIT` — amount <= 2000
  4. `EXCEEDS_DAILY_LIMIT` — daily_withdrawn + amount <= 2000
- A hold or a frozen customer does **NOT** block a withdrawal. Both the spec and
  the error table omit those codes for Withdraw. Hold blocks transfers only.
  This is intended, not an oversight.
- `daily_withdrawn` is a plain accumulator with **no date logic**. It resets when
  the database is reseeded, which is the point: this is the state-contamination
  demo. Canonical case: withdraw $1,500 (ok), then $1,000 → `EXCEEDS_DAILY_LIMIT`.
- On success: debit balance, add to `daily_withdrawn`, write a `withdraw`
  transaction with status `completed`.

## Transfer fan-out (SETTLED)
"Fan-out" is the CHAIN of dependency checks inside one transfer — customer
active → account exists → balance → hold → fraud-check service → recipient
enrolled — not multiple recipients in one request. It is what gives the pipeline
a multi-service scenario state model to build.

## Decisions taken while building (do not re-litigate silently)
- **An approval points at its transfer** through `approvals.transfer_ref`, which
  holds the id of the `pending` transactions row the transfer parked. Approving
  debits and completes that row; rejecting marks it `failed`. The money does not
  move while it sits pending.
- **Approvals are resolved** via `POST /approvals/{id}/approve|reject`, with
  `GET /approvals?status=pending` to list them. Deciding twice →
  `APPROVAL_ALREADY_RESOLVED`.
- **Bill Pay never fires on a timer.** `POST /scheduled-payments/run {asOfDate}`
  advances every due payment exactly ONE step, so tests control the clock and
  can still observe the intermediate `pending` state.
- **Cancelling deletes the row.** The frozen `status` enum has no `cancelled`
  value. `NOT_CANCELLABLE` fires for any status other than `scheduled`.
- **IP risk arrives as the optional `X-Ip-Risk` header** (0–100, default 0) and
  is passed straight through to the fraud service. Velocity is derived from
  `transactions` by the middleware.
- **`ACCOUNT_NOT_FOUND` is 422 everywhere**, including for an account that
  exists but belongs to someone else — saying "forbidden" would confirm it
  exists.
- **`POST /fraud-check` with a JSON body** is kept, rather than the spec's
  `GET ...?account=&amount=`. The body carries recipient-newness and velocity
  cleanly. **The POC spec should be amended to match**, since the test suite
  mocks this contract.

## Still open
1. **One account per customer.** Everything uses
   `findFirstByCustomerIdOrderByIdAsc` or an explicit id owned by the caller.
   Adding a second account per customer needs a deliberate pass.
2. **Should the README stay deliberately incomplete?** Open question 3 in the
   POC spec. It doubles as the brownfield "documentation" context, so how
   complete it is changes the difficulty of that demo.
3. **Brownfield exposure of the fraud service.** The scoring lives in
   `fraud-service` source. If the pipeline reads the whole repo as brownfield
   context it can simply read the weights instead of mocking the service.
   Consider excluding that directory from the brownfield context.

**Never document the fraud scoring internals in this repo's docs** (README,
CLAUDE.md). The weights and thresholds live in the fraud-service source only.
The suite must mock the service, not reverse-engineer it.

## DELIBERATE AMBIGUITY — do not resolve (Person A / Transfer)
Transfers over $10,000 route to an approval flow instead of completing. What
EXACTLY triggers "needs approval" is intentionally left vague in both code and
docs. Do NOT write a clean single rule. Build it so a reader genuinely cannot
tell whether the trigger is: amount>10k alone, OR amount>10k AND new recipient,
OR amount>10k OR fraud=review. This vagueness is the point — the testing
pipeline must FLAG it as an SME question rather than fabricate a rule. Keep the
README vague about it too.

## Tech
Java 17, Spring Boot 3.3.4, Spring Data JPA, Postgres 16, Flyway. React 18 +
TypeScript + Vite, plain fetch (no heavy state lib), React Router. The frontend
image runs `tsc --noEmit` before `vite build`, so a type error fails the build.
The UI session lives in React state only — no localStorage, no sessionStorage,
no cookie. Keep everything minimal and readable — this is a specimen,
clean-and-functional beats fancy.

## Seed personas (V2 + V3)
1 Alice Nguyen — $5,000, no hold. 2 enrolled + 1 un-enrolled recipient.
  **34 transactions, May–Aug 2026**, across completed/pending/failed — this is
  the account for date-range filtering and pagination. Also 4 scheduled
  payments, one in each state (scheduled / pending / paid / failed).
2 Brian Kowalski — $3,200, account hold = true. Blocks transfers, NOT withdrawals.
3 Chloe Ramos — $50, no transactions (insufficient funds + empty statement)
4 Dev Patel — $25,000, one enrolled + one un-enrolled recipient
  (large transfers, approval routing, fraud paths)
5 Frank Osei — **frozen**, $4,000, one enrolled recipient. The only way to
  reach `CUSTOMER_INACTIVE`.
6 Priya Shah — $6,000, `daily_withdrawn` already 1,800.00. Lets a SINGLE call
  hit the daily cap: withdraw 200 → exactly 2,000, allowed (inclusive boundary);
  withdraw 500 → `EXCEEDS_DAILY_LIMIT` without tripping the per-txn limit too.
