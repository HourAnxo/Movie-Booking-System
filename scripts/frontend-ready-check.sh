#!/usr/bin/env bash
#
# The two things a browser frontend needs that the API did not have:
#   1. a userId the client can actually use to book
#   2. CORS, so a page on another origin can call the gateway at all
#
#   bash scripts/frontend-ready-check.sh

GW=http://localhost:8083
ORIGIN=http://localhost:5173
pass=0; fail=0

check() { # label expected actual
  if [ "$2" = "$3" ]; then printf '  PASS  %-50s %s\n' "$1" "$3"; pass=$((pass+1))
  else printf '  FAIL  %-50s expected %s got %s\n' "$1" "$2" "$3"; fail=$((fail+1)); fi
}
hr() { printf '%s\n' "------------------------------------------------------------"; }

U="fe$RANDOM"

hr
echo "FIX 1 - the client is told who it is"
hr
curl -s -o /tmp/f -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"email\":\"$U@example.com\",\"password\":\"password123\",\"name\":\"Frontend Test\",\"phone\":\"0700000000\"}" >/dev/null 2>&1

TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/f)
UID_JSON=$(sed -n 's/.*"userId":\([0-9]*\).*/\1/p' /tmp/f | head -1)

if [ -z "$TOKEN" ]; then
  echo "ABORT: registration failed, nothing to check."
  head -c 300 /tmp/f; echo
  exit 1
fi

if [ -n "$UID_JSON" ]; then check "register response carries a userId" "yes" "yes"
else check "register response carries a userId" "yes" "no"; fi
echo "        userId=$UID_JSON"

# the same id must be inside the signed token, not just the body
PAYLOAD=$(printf '%s' "$TOKEN" | cut -d. -f2)
case $(( ${#PAYLOAD} % 4 )) in 2) PAYLOAD="$PAYLOAD==";; 3) PAYLOAD="$PAYLOAD=";; esac
CLAIM=$(printf '%s' "$PAYLOAD" | tr '_-' '/+' | base64 -d 2>/dev/null | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')
check "the signed token carries the same userId" "$UID_JSON" "$CLAIM"

# and /api/auth/profile must agree, so a page can recover it after a reload
curl -s -o /tmp/p "$GW/api/auth/profile" -H "Authorization: Bearer $TOKEN" >/dev/null 2>&1
check "GET /api/auth/profile returns it too" "$UID_JSON" "$(sed -n 's/.*"userId":\([0-9]*\).*/\1/p' /tmp/p | head -1)"

echo
echo "  the flow that was impossible before: book using that id"
SEAT=$(curl -s "$GW/api/seats" | sed -n 's/.*"seatId":\([0-9]*\)[^}]*"status":"AVAILABLE".*/\1/p' | head -1)
if [ -z "$SEAT" ]; then
  echo "        (no AVAILABLE seat in the catalogue — skipping the booking call)"
else
  code=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/bookings" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"userId\":$UID_JSON,\"showtimeId\":1,\"seatId\":$SEAT,\"totalAmount\":250.00}")
  check "POST /api/bookings with the token's own userId" 201 "$code"
  echo "        $(head -c 180 /tmp/b)"
fi

hr
echo "FIX 2 - a browser on another origin can call the API"
hr
# preflight: no Authorization header, exactly as a browser sends it
PRE=$(curl -s -o /tmp/c -D /tmp/h -w '%{http_code}' -X OPTIONS "$GW/api/bookings" \
  -H "Origin: $ORIGIN" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization,content-type')
check "preflight OPTIONS is not rejected as 401" 200 "$PRE"

ao=$(grep -i '^access-control-allow-origin:' /tmp/h | tr -d '\r' | awk '{print $2}')
check "preflight allows the frontend origin" "$ORIGIN" "$ao"
n=$(grep -ic '^access-control-allow-origin:' /tmp/h)
check "exactly one Allow-Origin header (not doubled)" "1" "$n"
echo "        allow-methods: $(grep -i '^access-control-allow-methods:' /tmp/h | tr -d '\r' | cut -d' ' -f2-)"

# and the real request carries the header too
curl -s -o /dev/null -D /tmp/h2 "$GW/api/movies" -H "Origin: $ORIGIN" >/dev/null 2>&1
check "actual GET also carries Allow-Origin" "$ORIGIN" "$(grep -i '^access-control-allow-origin:' /tmp/h2 | tr -d '\r' | awk '{print $2}')"

# an origin that is not on the list must be refused
curl -s -o /dev/null -D /tmp/h3 -X OPTIONS "$GW/api/bookings" \
  -H "Origin: http://evil.example.com" -H 'Access-Control-Request-Method: POST' >/dev/null 2>&1
bad=$(grep -ic '^access-control-allow-origin:' /tmp/h3)
check "an unlisted origin gets no Allow-Origin" "0" "$bad"

hr
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
