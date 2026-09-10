#!/usr/bin/env bash
#
# Role-based authorization, checked through the gateway against a running
# stack. Run from the project root:
#
#   bash scripts/authz-check.sh
#
# Registers a normal user, promotes a second account to ADMIN directly in
# auth_db (the first admin cannot be made through the API — nobody is one
# yet), then checks that each side can do exactly what it should.

GW=http://localhost:8083
pass=0; fail=0

check() { # label expected actual
  if [ "$2" = "$3" ]; then
    printf '  PASS  %-52s %s\n' "$1" "$3"; pass=$((pass+1))
  else
    printf '  FAIL  %-52s expected %s got %s\n' "$1" "$2" "$3"; fail=$((fail+1))
    printf '        %s\n' "$(head -c 200 /tmp/ab)"
  fi
}

register() { # username -> token
  local u=$1
  curl -s -o /tmp/reg -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$u\",\"email\":\"$u@example.com\",\"password\":\"password123\",\"name\":\"Authz Test\",\"phone\":\"0700000000\"}" >/dev/null 2>&1
  sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/reg
}

login() { # username -> token
  curl -s -o /tmp/log -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"password123\"}" >/dev/null 2>&1
  sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/log
}

call() { # method path token [body] -> status
  local m=$1 p=$2 t=$3 b=$4
  if [ -n "$b" ]; then
    curl -s -o /tmp/ab -w '%{http_code}' -X "$m" "$GW$p" \
      -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$b"
  else
    curl -s -o /tmp/ab -w '%{http_code}' -X "$m" "$GW$p" -H "Authorization: Bearer $t"
  fi
}

USER_NAME="authzuser$RANDOM"
ADMIN_NAME="authzadmin$RANDOM"

USER_TOKEN=$(register "$USER_NAME")
register "$ADMIN_NAME" >/dev/null

if [ -z "$USER_TOKEN" ]; then
  echo "ABORT: registration failed. Is the stack up?"; head -c 300 /tmp/reg; exit 1
fi

echo "promoting $ADMIN_NAME in auth_db (bootstrapping the first admin)..."
docker compose exec -T mysql mysql -uroot -proot -e \
  "UPDATE auth_db.users SET role='ADMIN' WHERE username='$ADMIN_NAME';" >/dev/null 2>&1

# A fresh login is required: the gateway reads the role from the token
# claim, so the old token still says USER.
ADMIN_TOKEN=$(login "$ADMIN_NAME")
if [ -z "$ADMIN_TOKEN" ]; then echo "ABORT: admin login failed"; exit 1; fi

echo
echo "=== the catalogue stays publicly readable ==="
check "GET /api/movies with no token at all" 200 "$(curl -s -o /tmp/ab -w '%{http_code}' $GW/api/movies)"

echo
echo "=== a normal USER must be refused operator actions ==="
check "USER POST   /api/movies"        403 "$(call POST   /api/movies       "$USER_TOKEN" '{"title":"Hacked","duration":90}')"
check "USER DELETE /api/movies/1"      403 "$(call DELETE /api/movies/1     "$USER_TOKEN")"
check "USER POST   /api/theaters"      403 "$(call POST   /api/theaters     "$USER_TOKEN" '{"name":"X","location":"Y"}')"
check "USER GET    /api/admin/users"   403 "$(call GET    /api/admin/users  "$USER_TOKEN")"
check "USER GET    /api/users (list)"  403 "$(call GET    /api/users        "$USER_TOKEN")"
check "USER DELETE /api/users/1"       403 "$(call DELETE /api/users/1      "$USER_TOKEN")"
check "USER PUT    role of another"    403 "$(call PUT "/api/auth/users/$USER_NAME/role" "$USER_TOKEN" '{"role":"ADMIN"}')"

echo
echo "=== a normal USER keeps its own surface ==="
check "USER GET    /api/auth/profile"  200 "$(call GET /api/auth/profile "$USER_TOKEN")"
check "USER GET    /api/bookings"      200 "$(call GET /api/bookings     "$USER_TOKEN")"

echo
echo "=== an ADMIN can do the operator actions ==="
check "ADMIN POST  /api/theaters"      201 "$(call POST /api/theaters    "$ADMIN_TOKEN" '{"name":"Authz Cinema","location":"Chennai"}')"
check "ADMIN GET   /api/admin/users"   200 "$(call GET  /api/admin/users "$ADMIN_TOKEN")"
check "ADMIN GET   /api/users (list)"  200 "$(call GET  /api/users       "$ADMIN_TOKEN")"

echo
echo "=== role changes go through the admin-only endpoint ==="
check "ADMIN promotes the normal user" 200 "$(call PUT "/api/auth/users/$USER_NAME/role" "$ADMIN_TOKEN" '{"role":"ADMIN"}')"
echo "        body: $(head -c 160 /tmp/ab)"
check "unknown role name is rejected"  400 "$(call PUT "/api/auth/users/$USER_NAME/role" "$ADMIN_TOKEN" '{"role":"SUPERADMIN"}')"

echo
echo "=== the promoted user only gains it on a NEW token ==="
check "old USER token still refused"   403 "$(call GET /api/admin/users "$USER_TOKEN")"
NEW_TOKEN=$(login "$USER_NAME")
check "after re-login, allowed"        200 "$(call GET /api/admin/users "$NEW_TOKEN")"

echo
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
