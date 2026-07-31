#!/bin/sh
# Smoke test for the shared foundation. Runs INSIDE the compose network and
# addresses services by their container names, so it exercises the same paths a
# browser does — including nginx's /api proxy — with nothing on the host involved.
#
#   docker compose --profile test run --rm smoke
#
# It checks wiring and the error contract only. Feature rules belong to the
# verticals and are not asserted here.

MIDDLEWARE="http://middleware:8080"
FRAUD="http://fraud-service:8081"
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

    if [ "$want_body" != "-" ] && ! echo "$body" | grep -q "$want_body"; then
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
echo "--- fraud service ---"
check "fraud-check stub"      200 '"decision":"approve"' -X POST "$FRAUD/fraud-check" -H "$json" \
    -d '{"accountId":1,"amount":500.00,"recipientId":2,"recipientIsNew":false,"ipRisk":0,"recentTransferCount":1}'

echo
echo "--- frontend + nginx ---"
check "spa index"             200 -  "$UI/"
check "spa deep link"         200 -  "$UI/dashboard"
check "proxy forwards login"  200 'Brian Kowalski'   -X POST "$UI/api/login" -H "$json" -d '{"customerId":2}'
check "proxy keeps reason"    404 'CUSTOMER_NOT_FOUND' -X POST "$UI/api/login" -H "$json" -d '{"customerId":999}'

echo
echo "passed: $passed  failed: $failed"
[ "$failed" -eq 0 ]
