#!/usr/bin/env bash
#
# Proves the two things the circuit breaker has to get right. Run against a
# running stack (docker compose up -d), from the project root:
#
#   bash scripts/resilience-check.sh
#
# It stops and restarts user-service, so do not point it at anything you care
# about.

GW=http://localhost:8083

hr() { printf '%s\n' "------------------------------------------------------------"; }

# /api/bookings is a protected route. Without a token every request below
# would stop at the gateway with 401 and never reach the breaker at all.
U="rescheck$RANDOM"
curl -s -o /tmp/reg -X POST "$GW/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"email\":\"$U@example.com\",\"password\":\"password123\",\"name\":\"Resilience Check\",\"phone\":\"0700000000\"}" >/dev/null 2>&1

TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/reg)

if [ -z "$TOKEN" ]; then
  echo "ABORT: could not obtain a token. Is the stack up and auth-service registered?"
  head -c 300 /tmp/reg
  exit 1
fi

# Valid in shape, so it gets past @Valid and actually reaches the
# user-service call that the breaker guards.
fire() {
  curl -s -o /tmp/rb -w '%{http_code} %{time_total}' \
    -X POST "$GW/api/bookings" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"userId\":$1,\"showtimeId\":1,\"seatId\":1,\"totalAmount\":10.00}" 2>/dev/null
}

hr
echo "PART 1 - a business outcome must NOT open the circuit"
echo "15 bookings for a user that does not exist: a 404 from a perfectly"
echo "healthy user-service. If these counted as failures, the breaker would"
echo "open and refuse bookings for everyone."
hr
declare -A seen1
for i in $(seq 1 15); do
  read -r code t <<<"$(fire 999999)"
  seen1[$code]=$(( ${seen1[$code]:-0} + 1 ))
done
for c in "${!seen1[@]}"; do echo "  HTTP $c  x${seen1[$c]}"; done
echo "  last body: $(head -c 200 /tmp/rb)"
echo
if [ -n "${seen1[503]}" ]; then
  echo "  RESULT: FAIL - circuit opened on business 404s"
else
  echo "  RESULT: PASS - circuit stayed closed through 15 business rejections"
fi

hr
echo "PART 2 - a real outage MUST open the circuit, then fail fast"
hr
echo "stopping user-service..."
docker compose stop user-service >/dev/null 2>&1

echo "firing 20 bookings for a valid user id:"
first_open=""
declare -A seen2
for i in $(seq 1 20); do
  read -r code t <<<"$(fire 1)"
  seen2[$code]=$(( ${seen2[$code]:-0} + 1 ))
  ms=$(awk -v x="$t" 'BEGIN{printf "%d", x*1000}')
  printf "  %2d  HTTP %s  %5sms\n" "$i" "$code" "$ms"
  if [ "$code" = "503" ] && [ -z "$first_open" ]; then first_open=$i; fi
done
echo "  last body: $(head -c 220 /tmp/rb)"
echo
if [ -n "$first_open" ]; then
  echo "  RESULT: PASS - circuit opened at request #$first_open (503 = refused without calling)"
else
  echo "  RESULT: FAIL - circuit never opened; every request paid the full failure cost"
fi

hr
echo "restarting user-service..."
docker compose start user-service >/dev/null 2>&1
echo "The breaker goes half-open after ~10s and closes once probes succeed."
hr
