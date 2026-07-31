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

**No feature/business logic exists yet.** No transfer, withdraw, billpay,
account, or approval rules. The fraud service returns a hardcoded
`{score: 0, decision: "approve"}` behind a TODO. Both verticals start from here.

What is in the middleware today:
- `com.mockbank.persistence` — 6 entities + 6 Spring Data repositories (shared)
- `com.mockbank.common` — `ErrorResponse`, `BusinessException`,
  `NotFoundException`, `NotAuthenticatedException`, `GlobalExceptionHandler`,
  `CustomerContext`
- `com.mockbank.auth` — `LoginController` (`POST /login`),
  `SessionController` (`GET /whoami`)

Feature owners add their OWN package (e.g. `com.mockbank.transfer`). Do not put
feature logic in `common`, `auth`, or `persistence`.

Known gap, intentionally left for Account Ops: the dashboard calls
`GET /accounts/me`, which does not exist yet. It currently shows the real error
instead of hiding it.

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

## Seed personas
1 Alice Nguyen — $5,000, no hold, 2 enrolled + 1 un-enrolled recipient, 4 txns
2 Brian Kowalski — $3,200, account hold = true
3 Chloe Ramos — $50 (insufficient-funds paths)
4 Dev Patel — $25,000, one enrolled recipient (large transfers / fraud paths)
