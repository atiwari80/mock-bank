#!/bin/sh
# Smoke test for the shared foundation. Runs INSIDE the compose network and
# addresses services by their container names, so it exercises the same paths a
# browser does — including nginx's /api proxy — with nothing on the host involved.
#
#   docker compose --profile test run --rm smoke
#
# Scope: wiring, the error contract, and — for flows that have shipped — that
# the endpoint exists, authenticates, and returns the seeded shape. It does NOT
# assert business rules (limits, fraud decisions, approval routing); those are
# the vertical owner's tests to write.

MIDDLEWARE="http://middleware:8080"
FRAUD="http://fraud-service:8081"
CREDIT="http://credit-check-service:8082"
UI="http://frontend:80"

passed=0
failed=0

# wait_for <url> — poll until the service answers or we give up.
wait_for() {
    i=0
    while [ "$i" -lt 60 ]; do
        if curl -s -o /dev/null -m 2 "$1"; then
            return 0
        fi
        i=$((i + 1))
        sleep 2
    done
    echo "TIMEOUT waiting for $1"
    return 1
}

# check <name> <expected-status> <expected-substring|-> <curl args...>
check() {
    name=$1
    want_status=$2
    want_body=$3
    shift 3

    status=$(curl -s -o /tmp/body -w '%{http_code}' "$@")
    body=$(cat /tmp/body)

    if [ "$status" != "$want_status" ]; then
        echo "FAIL  $name — expected status $want_status, got $status"
        echo "      body: $body"
        failed=$((failed + 1))
        return
    fi

    # -F: expectations are literal text, not regexes ("[]" must stay "[]").
    if [ "$want_body" != "-" ] && ! echo "$body" | grep -qF "$want_body"; then
        echo "FAIL  $name — body did not contain '$want_body'"
        echo "      body: $body"
        failed=$((failed + 1))
        return
    fi

    echo "ok    $name [$status]"
    passed=$((passed + 1))
}

json='Content-Type: application/json'

echo "waiting for services..."
wait_for "$MIDDLEWARE/login" || exit 1
wait_for "$FRAUD/fraud-check" || exit 1
wait_for "$CREDIT/credit-check" || exit 1
wait_for "$UI/" || exit 1

echo
echo "--- login ---"
check "login succeeds"        200 'Alice Nguyen'      -X POST "$MIDDLEWARE/login" -H "$json" -d '{"customerId":1}'
check "unknown customer 404"  404 'CUSTOMER_NOT_FOUND' -X POST "$MIDDLEWARE/login" -H "$json" -d '{"customerId":999}'

echo
echo "--- error contract ---"
check "missing field 400"     400 'BAD_REQUEST' -X POST "$MIDDLEWARE/login" -H "$json" -d '{}'
check "malformed body 400"    400 'BAD_REQUEST' -X POST "$MIDDLEWARE/login" -H "$json" -d 'not-json'
check "unknown route 404"     404 'NOT_FOUND'   "$MIDDLEWARE/nope"
check "wrong method 405"      405 'METHOD_NOT_ALLOWED' -X GET "$MIDDLEWARE/login"
check "no header still 401"   401 'NOT_AUTHENTICATED' "$MIDDLEWARE/whoami"
check "bad header 401"        401 'NOT_AUTHENTICATED' "$MIDDLEWARE/whoami" -H 'X-Customer-Id: nonsense'
check "unknown customer 401"  401 'NOT_AUTHENTICATED' "$MIDDLEWARE/whoami" -H 'X-Customer-Id: 999'
check "valid header resolves" 200 'Alice Nguyen' "$MIDDLEWARE/whoami" -H 'X-Customer-Id: 1'

echo
echo "--- account ops: summary + statement (shipped) ---"
# Shape and seeded values only — no business rules asserted here.
check "account summary"       200 '"balance":5000.00'  "$MIDDLEWARE/accounts/me" -H 'X-Customer-Id: 1'
check "summary reports hold"  200 '"hold":true'        "$MIDDLEWARE/accounts/me" -H 'X-Customer-Id: 2'
check "summary needs auth"    401 'NOT_AUTHENTICATED'  "$MIDDLEWARE/accounts/me"
check "statement lists txns"  200 '"type":"billpay"'   "$MIDDLEWARE/accounts/me/transactions" -H 'X-Customer-Id: 1'
# Newest-first ordering: id 34 (2026-08-01) is the latest row in the seed.
check "statement newest 1st"  200 '[{"id":34'          "$MIDDLEWARE/accounts/me/transactions" -H 'X-Customer-Id: 1'
check "statement empty is []" 200 '[]'                 "$MIDDLEWARE/accounts/me/transactions" -H 'X-Customer-Id: 3'
check "statement needs auth"  401 'NOT_AUTHENTICATED'  "$MIDDLEWARE/accounts/me/transactions"

echo
echo "--- seed fixtures the spec's test cases depend on ---"
# These guard the V3 seed: if a migration drops them, whole spec scenarios
# silently become untestable rather than failing loudly.
check "frozen customer seeded" 200 '"status":"frozen"' "$MIDDLEWARE/whoami" -H 'X-Customer-Id: 5'
check "daily cap part-used"    200 '"dailyWithdrawn":1800.00' "$MIDDLEWARE/accounts/me" -H 'X-Customer-Id: 6'
check "deep history for paging" 200 '"id":34' "$MIDDLEWARE/accounts/me/transactions" -H 'X-Customer-Id: 1'
check "history spans months"   200 '2026-05-04' "$MIDDLEWARE/accounts/me/transactions" -H 'X-Customer-Id: 1'

echo
echo "--- flow 4: statement paging + date filter (read-only) ---"
check "account by id"         200 '"id":1'            "$MIDDLEWARE/account/1" -H 'X-Customer-Id: 1'
check "someone else account"  422 'ACCOUNT_NOT_FOUND' "$MIDDLEWARE/account/4" -H 'X-Customer-Id: 1'
check "statement page 0"      200 '"page":0'          "$MIDDLEWARE/transactions/1?page=0&size=10" -H 'X-Customer-Id: 1'
check "statement page size"   200 '"size":10'         "$MIDDLEWARE/transactions/1?page=0&size=10" -H 'X-Customer-Id: 1'
check "statement page 2"      200 '"page":2'          "$MIDDLEWARE/transactions/1?page=2&size=10" -H 'X-Customer-Id: 1'
# May 2026 holds exactly 9 seeded rows, and nothing this run creates lands there.
check "date filter narrows"   200 '"totalItems":9'    "$MIDDLEWARE/transactions/1?from=2026-05-01&to=2026-05-31" -H 'X-Customer-Id: 1'

echo
echo "--- fraud service ---"
check "fraud-check stub"      200 '"decision":"approve"' -X POST "$FRAUD/fraud-check" -H "$json" \
    -d '{"accountId":1,"amount":500.00,"recipientId":2,"recipientIsNew":false,"ipRisk":0,"recentTransferCount":1}'

echo
echo "--- credit check service ---"
check "credit-check stub"     200 '"score"' "$CREDIT/credit-check?ssn=111111111&customerId=1"

echo
echo "--- frontend + nginx ---"
check "spa index"             200 -  "$UI/"
check "spa deep link"         200 -  "$UI/dashboard"
check "proxy forwards login"  200 'Brian Kowalski'   -X POST "$UI/api/login" -H "$json" -d '{"customerId":2}'
check "proxy keeps reason"    404 'CUSTOMER_NOT_FOUND' -X POST "$UI/api/login" -H "$json" -d '{"customerId":999}'
check "proxy passes header"   200 '"balance":25000.00' "$UI/api/accounts/me" -H 'X-Customer-Id: 4'
check "statements deep link"  200 -  "$UI/statements"

# ---------------------------------------------------------------------------
# Everything below MUTATES state, so it runs last: the read-only assertions
# above depend on the seeded balances being untouched.
# ---------------------------------------------------------------------------

echo
echo "--- withdraw ---"
check "withdraw succeeds"     200 '"transactionId"'     -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 1' -d '{"account":1,"amount":100.00}'
check "hold does not block"   200 '"transactionId"'     -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 2' -d '{"account":2,"amount":100.00}'
check "not your account"      422 'ACCOUNT_NOT_FOUND'   -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 1' -d '{"account":4,"amount":10.00}'
check "no money"              422 'INSUFFICIENT_FUNDS'  -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 3' -d '{"account":3,"amount":100.00}'
# Funds are checked before the caps, so this stays INSUFFICIENT_FUNDS.
check "broke beats the cap"   422 'INSUFFICIENT_FUNDS'  -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 3' -d '{"account":3,"amount":2500.00}'
check "over per-txn cap"      422 'EXCEEDS_TXN_LIMIT'   -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 1' -d '{"account":1,"amount":2500.00}'
check "over daily cap"        422 'EXCEEDS_DAILY_LIMIT' -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 6' -d '{"account":6,"amount":500.00}'
# 1800 + 200 = exactly 2000: the boundary is inclusive, so this must pass.
check "cap boundary allows"   200 '"dailyWithdrawn":2000.00' -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 6' -d '{"account":6,"amount":200.00}'
check "one cent past the cap" 422 'EXCEEDS_DAILY_LIMIT' -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 6' -d '{"account":6,"amount":0.01}'
check "amount must be > 0"    400 'BAD_REQUEST'         -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 1' -d '{"account":1,"amount":-5}'
# The spec's own state-contamination example, on an untouched account: neither
# call breaks a rule on its own, but the second one fails because the first ran.
check "spec: 1500 allowed"    200 '"dailyWithdrawn":1500.00' -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 5' -d '{"account":5,"amount":1500.00}'
check "spec: then 1000 fails" 422 'EXCEEDS_DAILY_LIMIT'      -X POST "$MIDDLEWARE/withdraw" -H "$json" -H 'X-Customer-Id: 5' -d '{"account":5,"amount":1000.00}'

echo
echo "--- transfer fan-out ---"
check "transfer succeeds"     200 '"status":"completed"'    -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 1' -d '{"fromAccount":1,"toRecipient":2,"amount":250.00}'
check "recipient not enrolled" 422 'RECIPIENT_NOT_ENROLLED' -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 1' -d '{"fromAccount":1,"toRecipient":3,"amount":50.00}'
check "account hold blocks"   422 'ACCOUNT_HOLD'            -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 2' -d '{"fromAccount":2,"toRecipient":2,"amount":50.00}'
check "frozen customer"       422 'CUSTOMER_INACTIVE'       -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 5' -d '{"fromAccount":5,"toRecipient":5,"amount":50.00}'
check "transfer no funds"     422 'INSUFFICIENT_FUNDS'      -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 3' -d '{"fromAccount":3,"toRecipient":1,"amount":500.00}'
check "recipients listed"     200 'Riverside Property Mgmt' "$MIDDLEWARE/recipients" -H 'X-Customer-Id: 1'

echo
echo "--- fraud branches (real service, not mocked) ---"
check "fraud declines"        422 'FRAUD_DECLINE' -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 4' -H 'X-Ip-Risk: 100' -d '{"fromAccount":4,"toRecipient":4,"amount":20000.00}'
check "fraud holds review"    422 'FRAUD_REVIEW'  -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 1' -H 'X-Ip-Risk: 100' -d '{"fromAccount":1,"toRecipient":2,"amount":4000.00}'

echo
echo "--- large-transfer approval ---"
# Two parked transfers so both endings can be walked: one approved, one rejected.
# Neither debits while pending, so the second still passes the funds check.
check "large goes to approval" 200 '"status":"pending_approval"' -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 4' -d '{"fromAccount":4,"toRecipient":4,"amount":15000.00}'
check "second large parks too" 200 '"status":"pending_approval"' -X POST "$MIDDLEWARE/transfer" -H "$json" -H 'X-Customer-Id: 4' -d '{"fromAccount":4,"toRecipient":4,"amount":12000.00}'
check "approval is pending"   200 '"status":"pending"'   "$MIDDLEWARE/approvals?status=pending" -H 'X-Customer-Id: 4'
check "reject voids transfer" 200 '"status":"rejected"'  -X POST "$MIDDLEWARE/approvals/101/reject" -H 'X-Customer-Id: 4'
check "voided txn is failed"  200 '"status":"failed"'    "$MIDDLEWARE/transactions/4?page=0&size=10" -H 'X-Customer-Id: 4'
check "approve completes"     200 '"status":"approved"'  -X POST "$MIDDLEWARE/approvals/100/approve" -H 'X-Customer-Id: 4'
check "approved txn settles"  200 '"balance":10000.00'   "$MIDDLEWARE/account/4" -H 'X-Customer-Id: 4'
check "cannot decide twice"   422 'APPROVAL_ALREADY_RESOLVED' -X POST "$MIDDLEWARE/approvals/100/approve" -H 'X-Customer-Id: 4'
check "rejected stays final"  422 'APPROVAL_ALREADY_RESOLVED' -X POST "$MIDDLEWARE/approvals/101/reject" -H 'X-Customer-Id: 4'

echo
echo "--- bill pay state machine ---"
check "schedule a payment"    200 '"status":"scheduled"'  -X POST "$MIDDLEWARE/schedule-payment" -H "$json" -H 'X-Customer-Id: 1' -d '{"payee":"Harbour Telecom","amount":54.00,"date":"2026-09-01"}'
check "payments listed"       200 'Harbour Telecom'       "$MIDDLEWARE/scheduled-payments" -H 'X-Customer-Id: 1'
check "cancel a scheduled"    200 '"cancelled":true'      -X POST "$MIDDLEWARE/scheduled-payments/1/cancel" -H 'X-Customer-Id: 1'
check "cannot cancel paid"    422 'NOT_CANCELLABLE'       -X POST "$MIDDLEWARE/scheduled-payments/3/cancel" -H 'X-Customer-Id: 1'
# Payment 2 is already 'pending' and due, so one run settles it.
check "run settles due"       200 '"paid":1'              -X POST "$MIDDLEWARE/scheduled-payments/run" -H "$json" -H 'X-Customer-Id: 1' -d '{"asOfDate":"2026-08-10"}'
# A payment bigger than the balance it will draw on: it schedules fine, queues
# fine, and only fails on the day it actually fires.
check "schedule beyond funds" 200 '"status":"scheduled"'  -X POST "$MIDDLEWARE/schedule-payment" -H "$json" -H 'X-Customer-Id: 3' -d '{"payee":"Rent","amount":500.00,"date":"2026-08-01"}'
check "run queues it"         200 '"queued":1'            -X POST "$MIDDLEWARE/scheduled-payments/run" -H "$json" -H 'X-Customer-Id: 3' -d '{"asOfDate":"2026-08-10"}'
check "fires and fails"       200 '"failed":1'            -X POST "$MIDDLEWARE/scheduled-payments/run" -H "$json" -H 'X-Customer-Id: 3' -d '{"asOfDate":"2026-08-10"}'
check "payment shows failed"  200 '"status":"failed"'     "$MIDDLEWARE/scheduled-payments" -H 'X-Customer-Id: 3'

echo
echo "--- credit card application ---"
# Read-only guard: confirm the credit-check service returns the seeded score for SSN 111111111.
# All apply calls below are mutating (they create credit_application rows).
check "credit-card: happy path"       200 '"status":"approved"'    -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 1' -d '{"customerId":1,"ssn":"111111111","requestedLimit":5000}'
check "credit-card: unknown customer" 401 'NOT_AUTHENTICATED'       -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 999' -d '{"customerId":999,"ssn":"111111111","requestedLimit":5000}'
check "credit-card: frozen customer"  422 'CUSTOMER_INACTIVE'       -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 5' -d '{"customerId":5,"ssn":"555555555","requestedLimit":5000}'
check "credit-card: ssn too short"    422 'INVALID_SSN'             -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 1' -d '{"customerId":1,"ssn":"12345","requestedLimit":5000}'
check "credit-card: ssn has letters"  422 'INVALID_SSN'             -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 1' -d '{"customerId":1,"ssn":"12345678A","requestedLimit":5000}'
check "credit-card: score declined"   422 'CREDIT_DECLINE'          -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 3' -d '{"customerId":3,"ssn":"333333333","requestedLimit":2000}'
check "credit-card: bankruptcy flag"  422 'FRAUD_DECLINE'           -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 1' -d '{"customerId":1,"ssn":"999999999","requestedLimit":5000}'
# SSN 444444444 returns a bureau score that falls exactly at the approval boundary.
check "credit-card: borderline score" 422 'CREDIT_DECLINE'          -X POST "$MIDDLEWARE/credit-card/apply" -H "$json" -H 'X-Customer-Id: 4' -d '{"customerId":4,"ssn":"444444444","requestedLimit":3000}'

echo
echo "passed: $passed  failed: $failed"
[ "$failed" -eq 0 ]
