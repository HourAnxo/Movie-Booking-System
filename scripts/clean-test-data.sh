#!/usr/bin/env bash
#
# Removes the rows the test scripts left behind, keeping the demo catalogue
# that seed-demo-data.sh created. Run from the project root:
#
#   bash scripts/clean-test-data.sh
#
# DESTRUCTIVE. It deletes every booking, payment and user account, plus the
# throwaway movies and theaters named by the test suites. The seeded movies,
# theaters, screens, seats and showtimes are kept.
#
# It finishes by creating two clean accounts through the API rather than by
# inserting rows, so the passwords are properly hashed and each one gets a
# linked profile in user-service.

set -u
GW=http://localhost:8083

KEEP_THEATERS="'Grand Cinemas','Aurora Multiplex'"
KEEP_MOVIES="'Interstellar','The Dark Knight','Spirited Away','Parasite','Vikram','Dune: Part Three'"

sql() { docker compose exec -T mysql mysql -uroot -proot -N -e "$1" 2>/dev/null; }

echo "Before:"
sql "SELECT CONCAT('  movies=',  (SELECT COUNT(*) FROM movie_db.movies),
                   ' theaters=', (SELECT COUNT(*) FROM theater_db.theaters),
                   ' screens=',  (SELECT COUNT(*) FROM screen_db.screens),
                   ' seats=',    (SELECT COUNT(*) FROM seat_db.seats),
                   ' bookings=', (SELECT COUNT(*) FROM booking_db.bookings),
                   ' users=',    (SELECT COUNT(*) FROM auth_db.users));"

# ---------------------------------------------------------------------------
# Order matters. Bookings hold seats, so they go first and the seats they
# held are released — otherwise the demo opens with seats marked BOOKED and
# no booking anywhere explaining why.
# ---------------------------------------------------------------------------

echo
echo "Deleting bookings and payments…"
sql "DELETE FROM payment_db.payments;"
sql "DELETE FROM booking_db.bookings;"

echo "Releasing every seat back to AVAILABLE…"
sql "UPDATE seat_db.seats SET status = 'AVAILABLE' WHERE status <> 'AVAILABLE';"

# ---------------------------------------------------------------------------
# Screens belonging to a discarded theater go too, along with their seats and
# showtimes. There are no foreign keys between services, so nothing cascades
# on its own — leaving them behind would give the UI screens whose theater
# does not exist.
# ---------------------------------------------------------------------------

echo "Finding screens on theaters that are being removed…"
KEEP_THEATER_IDS=$(sql "SELECT GROUP_CONCAT(theater_id) FROM theater_db.theaters WHERE name IN ($KEEP_THEATERS);")
if [ -z "$KEEP_THEATER_IDS" ] || [ "$KEEP_THEATER_IDS" = "NULL" ]; then
  echo "ABORT: none of the seeded theaters are present — run seed-demo-data.sh first."
  exit 1
fi
echo "  keeping theaters: $KEEP_THEATER_IDS"

DROP_SCREENS=$(sql "SELECT GROUP_CONCAT(screen_id) FROM screen_db.screens WHERE theater_id NOT IN ($KEEP_THEATER_IDS);")
if [ -n "$DROP_SCREENS" ] && [ "$DROP_SCREENS" != "NULL" ]; then
  echo "  dropping screens: $DROP_SCREENS (and their seats and showtimes)"
  sql "DELETE FROM seat_db.seats          WHERE screen_id IN ($DROP_SCREENS);"
  sql "DELETE FROM showtime_db.showtimes  WHERE screen_id IN ($DROP_SCREENS);"
  sql "DELETE FROM screen_db.screens      WHERE screen_id IN ($DROP_SCREENS);"
else
  echo "  no orphan screens"
fi

echo "Deleting throwaway theaters and movies…"
sql "DELETE FROM theater_db.theaters WHERE name NOT IN ($KEEP_THEATERS);"
sql "DELETE FROM movie_db.movies     WHERE title NOT IN ($KEEP_MOVIES);"

# A showtime pointing at a deleted movie would render as a blank row.
sql "DELETE FROM showtime_db.showtimes
      WHERE movie_id NOT IN (SELECT movie_id FROM movie_db.movies);"

# ---------------------------------------------------------------------------
# Accounts. Both tables, or auth_db and user_db drift apart — which is the
# one thing AuthServiceImpl.register goes out of its way to prevent.
# ---------------------------------------------------------------------------

echo "Deleting all accounts (both auth_db and user_db)…"
sql "DELETE FROM auth_db.users;"
sql "DELETE FROM user_db.users;"

# ---------------------------------------------------------------------------

echo
echo "Creating clean accounts through the API…"

make_user() { # username email name
  curl -s -o /tmp/clean_reg -w '%{http_code}' -X POST "$GW/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"email\":\"$2\",\"password\":\"password123\",\"name\":\"$3\",\"phone\":\"0700000000\"}"
}

code=$(make_user admin admin@example.com "Site Admin")
if [ "$code" = "201" ]; then
  docker compose exec -T mysql mysql -uroot -proot \
    -e "UPDATE auth_db.users SET role='ADMIN' WHERE username='admin';" >/dev/null 2>&1
  echo "  + admin (promoted to ADMIN)"
else
  echo "  ! admin -> $code $(head -c 150 /tmp/clean_reg)"
fi

code=$(make_user demo demo@example.com "Demo User")
[ "$code" = "201" ] && echo "  + demo (USER)" || echo "  ! demo -> $code"

echo
echo "After:"
sql "SELECT CONCAT('  movies=',  (SELECT COUNT(*) FROM movie_db.movies),
                   ' theaters=', (SELECT COUNT(*) FROM theater_db.theaters),
                   ' screens=',  (SELECT COUNT(*) FROM screen_db.screens),
                   ' seats=',    (SELECT COUNT(*) FROM seat_db.seats),
                   ' showtimes=',(SELECT COUNT(*) FROM showtime_db.showtimes),
                   ' bookings=', (SELECT COUNT(*) FROM booking_db.bookings),
                   ' users=',    (SELECT COUNT(*) FROM auth_db.users));"

echo
echo "Sign in at http://localhost:5173"
echo "  admin / password123   (ADMIN — sees the Admin tab)"
echo "  demo  / password123   (normal user)"
