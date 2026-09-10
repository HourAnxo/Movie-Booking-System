#!/usr/bin/env bash
#
# Proves the actuator wiring is load-bearing rather than decorative. Run
# from the project root against a running stack:
#
#   bash scripts/health-check.sh
#
# Part 2 stops MySQL, so do not point this at anything you care about.

pass=0; fail=0

# service:port, matching EXPOSE in each Dockerfile.
SERVICES="discovery-server:8761 api-gateway:8083 auth-service:8082 theater-service:8084
screen-service:8085 showtime-service:8086 seat-service:8087 booking-service:8088
payment-service:8089 user-service:8090 movie-service:8091 admin-service:8093"

check() { # label expected actual
  if [ "$2" = "$3" ]; then printf '  PASS  %-44s %s\n' "$1" "$3"; pass=$((pass+1))
  else printf '  FAIL  %-44s expected %s got %s\n' "$1" "$2" "$3"; fail=$((fail+1)); fi
}

health() { # service port -> status word
  docker compose exec -T "$1" curl -sS "http://localhost:$2/actuator/health" 2>/dev/null \
    | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p' | head -1
}

hr() { printf '%s\n' "------------------------------------------------------------"; }

hr
echo "PART 1 - every service serves its own health endpoint"
hr
for entry in $SERVICES; do
  s=${entry%%:*}; p=${entry##*:}
  check "$s /actuator/health" "UP" "$(health "$s" "$p")"
done

hr
echo "PART 2 - Docker agrees (the HEALTHCHECK in each image)"
hr
for entry in $SERVICES; do
  s=${entry%%:*}
  check "$s container status" "healthy" "$(docker inspect --format '{{.State.Health.Status}}' "mbs-$s" 2>/dev/null)"
done

hr
echo "PART 3 - health is REAL: a broken database must read DOWN"
echo "A service that answers UP with no database behind it is worse than"
echo "having no health check, because orchestration believes it."
hr
echo "stopping mysql..."
docker compose stop mysql >/dev/null 2>&1
sleep 15

st=$(health booking-service 8088)
check "booking-service with mysql stopped" "DOWN" "$st"
docker compose exec -T booking-service curl -sS http://localhost:8088/actuator/health 2>/dev/null | head -c 220; echo

# the gateway owns no datasource, so it must be unaffected
check "api-gateway (no datasource) stays UP" "UP" "$(health api-gateway 8083)"

hr
echo "restarting mysql..."
docker compose start mysql >/dev/null 2>&1
st=""
for i in $(seq 1 40); do
  st=$(health booking-service 8088)
  [ "$st" = "UP" ] && { echo "recovered after ~$((i*3))s"; break; }
  sleep 3
done
check "booking-service recovers to UP" "UP" "$st"

hr
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
