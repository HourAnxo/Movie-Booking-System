#!/usr/bin/env bash
#
# Checks the running stack through the gateway only, the way a real client
# reaches it: bad bodies are rejected as 400 with the offending fields named,
# unauthenticated calls are 401, catalogue reads are public, and valid bodies
# still go through. Run it after `docker compose up -d`, once the gateway
# answers (Eureka registration takes roughly 30 seconds).
#
#   bash scripts/smoke-test.sh
#
# Catalogue writes need ADMIN, so this promotes its own account in auth_db
# and logs in again — the gateway reads the role from the token claim, so a
# promotion is invisible until a new token is issued.

GW=http://localhost:8083
pass=0; fail=0

check() { # name expected actual body
  if [ "$2" = "$3" ]; then echo "  PASS  $1 ($3)"; pass=$((pass+1));
  else echo "  FAIL  $1 — expected $2 got $3"; echo "        $4" | head -c 400; echo; fail=$((fail+1)); fi
}

U="smoke$RANDOM"

echo "1. register with a BAD body (short password, bad email, blank name)"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"email\":\"not-an-email\",\"password\":\"short\",\"name\":\"\",\"phone\":\"1\"}")
check "invalid register -> 400" 400 "$R" "$(cat /tmp/b)"
echo "        body: $(cat /tmp/b)"

echo "2. register with a GOOD body"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"email\":\"$U@example.com\",\"password\":\"password123\",\"name\":\"Smoke Test\",\"phone\":\"0700000000\"}")
check "valid register -> 201" 201 "$R" "$(cat /tmp/b)"
USER_TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/b)

echo "3. protected route with NO token"
R=$(curl -s -o /tmp/b -w '%{http_code}' "$GW/api/bookings")
check "no token -> 401" 401 "$R" "$(cat /tmp/b)"

echo "4. public catalogue read with no token"
R=$(curl -s -o /tmp/b -w '%{http_code}' "$GW/api/movies")
check "public GET /api/movies -> 200" 200 "$R" "$(cat /tmp/b)"

echo "5. catalogue write as a plain USER is refused"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/movies" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Should Not Exist","duration":100}')
check "USER POST /api/movies -> 403" 403 "$R" "$(cat /tmp/b)"

# Promote and re-login so the remaining validation cases can reach the
# controllers at all.
docker compose exec -T mysql mysql -uroot -proot -e \
  "UPDATE auth_db.users SET role='ADMIN' WHERE username='$U';" >/dev/null 2>&1
curl -s -o /tmp/b -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"password123\"}" >/dev/null 2>&1
TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/b)

echo "6. movie write as ADMIN, INVALID body (blank title, rating 99)"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/movies" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"","rating":99.5,"duration":-5}')
check "invalid movie -> 400" 400 "$R" "$(cat /tmp/b)"
echo "        body: $(cat /tmp/b)"

echo "7. showtime with endTime BEFORE startTime (the cross-field rule)"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/showtimes" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"movieId":1,"theaterId":1,"screenId":1,"showDate":"2026-01-01","startTime":"20:00:00","endTime":"18:00:00"}')
check "endTime before startTime -> 400" 400 "$R" "$(cat /tmp/b)"
echo "        body: $(cat /tmp/b)"

echo "8. unparseable body (a value outside an enum's closed set)"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/movies" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Bad Status","status":"NOT_A_STATUS"}')
check "unknown enum value -> 400" 400 "$R" "$(cat /tmp/b)"

echo "9. booking with negative amount and null seatId"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/bookings" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"userId":1,"showtimeId":1,"seatId":null,"totalAmount":-5}')
check "invalid booking -> 400" 400 "$R" "$(cat /tmp/b)"
echo "        body: $(cat /tmp/b)"

echo "10. HAPPY PATH — valid theater as ADMIN"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/theaters" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Smoke Cinema","location":"Chennai","address":"12 Test Road"}')
check "valid theater -> 201" 201 "$R" "$(cat /tmp/b)"
TID=$(sed -n 's/.*"theaterId":\([0-9]*\).*/\1/p' /tmp/b)

echo "11. HAPPY PATH — it persisted and reads back (publicly)"
R=$(curl -s -o /tmp/b -w '%{http_code}' "$GW/api/theaters/$TID")
check "GET the new theater -> 200" 200 "$R" "$(cat /tmp/b)"
echo "        body: $(cat /tmp/b)"

echo "12. HAPPY PATH — valid movie with optional fields omitted"
R=$(curl -s -o /tmp/b -w '%{http_code}' -X POST "$GW/api/movies" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Smoke Movie","duration":120,"rating":8.5}')
check "valid movie, no status field -> 201" 201 "$R" "$(cat /tmp/b)"
echo "        body: $(cat /tmp/b)"

echo
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
