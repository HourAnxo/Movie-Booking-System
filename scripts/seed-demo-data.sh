#!/usr/bin/env bash
#
# Fills an empty catalogue so the frontend has something to show. Run from
# the project root against a running stack:
#
#   bash scripts/seed-demo-data.sh
#
# Additive, not idempotent: every run creates new theaters, screens and
# seats. Seat numbers are unique per screen, so re-running is safe only
# because each run makes fresh screens.
#
# It registers its own account and promotes it in auth_db, because the
# catalogue is ADMIN-only to write and the first admin cannot be made
# through the API.

set -u
GW=http://localhost:8083

json() { sed -n "s/.*\"$2\":\([0-9]*\).*/\1/p" "$1" | head -1; }

# ---------------------------------------------------------------- admin --

ADMIN="seeder$RANDOM"
curl -s -o /tmp/seed_reg -X POST "$GW/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN\",\"email\":\"$ADMIN@example.com\",\"password\":\"password123\",\"name\":\"Demo Seeder\",\"phone\":\"0700000000\"}" >/dev/null 2>&1

if ! grep -q accessToken /tmp/seed_reg 2>/dev/null; then
  echo "ABORT: could not register. Is the stack up and past Eureka registration?"
  head -c 300 /tmp/seed_reg; echo
  exit 1
fi

docker compose exec -T mysql mysql -uroot -proot \
  -e "UPDATE auth_db.users SET role='ADMIN' WHERE username='$ADMIN';" >/dev/null 2>&1

# The gateway reads the role from the token claim, so the promotion is
# invisible until a new token is issued.
curl -s -o /tmp/seed_login -X POST "$GW/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN\",\"password\":\"password123\"}" >/dev/null 2>&1

TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' /tmp/seed_login)
if [ -z "$TOKEN" ]; then echo "ABORT: admin login failed"; exit 1; fi

post() { # path json -> writes /tmp/seed_out, echoes status
  curl -s -o /tmp/seed_out -w '%{http_code}' -X POST "$GW$1" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$2"
}

echo "seeding as $ADMIN (ADMIN)"

# --------------------------------------------------------------- movies --

echo
echo "Movies"
add_movie() { # title genre duration language rating status
  code=$(post /api/movies "{\"title\":\"$1\",\"genre\":\"$2\",\"duration\":$3,\"language\":\"$4\",\"rating\":$5,\"status\":\"$6\"}")
  if [ "$code" = "201" ]; then
    echo "  + $1 (#$(json /tmp/seed_out movieId))"
  else
    echo "  ! $1 -> $code $(head -c 120 /tmp/seed_out)"
  fi
}

add_movie "Interstellar"          "Sci-Fi"   169 "English" 8.6 NOW_SHOWING
add_movie "The Dark Knight"       "Action"   152 "English" 9.0 NOW_SHOWING
add_movie "Spirited Away"         "Animation" 125 "Japanese" 8.6 NOW_SHOWING
add_movie "Parasite"              "Thriller" 132 "Korean"  8.5 NOW_SHOWING
add_movie "Vikram"                "Action"   174 "Tamil"   8.3 NOW_SHOWING
# MovieStatus is a closed set: NOW_SHOWING, COMING_SOON, ARCHIVED. Anything
# else is rejected by Jackson before validation runs, as a 400.
add_movie "Dune: Part Three"      "Sci-Fi"   166 "English" 8.1 COMING_SOON

# ------------------------------------------------------ theaters/screens --

echo
echo "Theaters, screens and seats"

SCREEN_IDS=""
SEAT_ROWS="A B C D E"
SEAT_COLS="1 2 3 4 5 6 7 8"

add_theater() { # name location address
  code=$(post /api/theaters "{\"name\":\"$1\",\"location\":\"$2\",\"address\":\"$3\"}")
  [ "$code" = "201" ] || { echo "  ! theater $1 -> $code"; return 1; }
  json /tmp/seed_out theaterId
}

add_screen() { # theaterId name type capacity
  code=$(post /api/screens "{\"theaterId\":$1,\"name\":\"$2\",\"screenType\":\"$3\",\"capacity\":$4}")
  [ "$code" = "201" ] || { echo "  ! screen $2 -> $code"; return 1; }
  json /tmp/seed_out screenId
}

fill_seats() { # screenId
  local screen=$1 made=0 failed=0
  for row in $SEAT_ROWS; do
    for col in $SEAT_COLS; do
      # Front two rows are cheaper seating in most cinemas; the type is
      # free text in the schema, so this is only for display.
      local type=STANDARD
      case "$row" in A|B) type=STANDARD ;; C|D) type=PREMIUM ;; E) type=RECLINER ;; esac
      code=$(post /api/seats "{\"screenId\":$screen,\"seatNumber\":\"$row$col\",\"seatType\":\"$type\"}")
      if [ "$code" = "201" ]; then made=$((made+1)); else failed=$((failed+1)); fi
    done
  done
  echo "      $made seats created$( [ "$failed" -gt 0 ] && echo ", $failed failed" )"
}

for spec in "Grand Cinemas|Chennai|12 Mount Road" "Aurora Multiplex|Bengaluru|88 MG Road"; do
  IFS='|' read -r tname tloc taddr <<EOF
$spec
EOF
  tid=$(add_theater "$tname" "$tloc" "$taddr") || continue
  echo "  + $tname (#$tid)"

  for sspec in "Screen 1|IMAX|40" "Screen 2|2D|40"; do
    IFS='|' read -r sname stype scap <<EOF
$sspec
EOF
    sid=$(add_screen "$tid" "$sname" "$stype" "$scap") || continue
    echo "    + $sname (#$sid, $stype)"
    SCREEN_IDS="$SCREEN_IDS $sid:$tid"
    fill_seats "$sid"
  done
done

# ------------------------------------------------------------ showtimes --

echo
echo "Showtimes"

# Three slots a day for the next three days, across every screen made above.
#
# Only NOW_SHOWING titles are scheduled — a COMING_SOON film has not been
# released, so a showtime against it would be nonsense.
#
# endTime must be after startTime (ShowtimeRequestDTO enforces it with
# @AssertTrue), and a show crossing midnight is not representable — two TIME
# columns and one date — so the last slot ends well before 23:59.
MOVIE_IDS=$(curl -s "$GW/api/movies/status/NOW_SHOWING" | grep -o '"movieId":[0-9]*' | cut -d: -f2)
MOVIE_COUNT=$(printf '%s\n' "$MOVIE_IDS" | grep -c .)

if [ "$MOVIE_COUNT" -eq 0 ]; then
  echo "  ! no NOW_SHOWING movies — skipping showtimes"
  MOVIE_COUNT=1
fi

i=0
for entry in $SCREEN_IDS; do
  # Each entry is screenId:theaterId. The theater has to be the one that
  # actually owns the screen — there are no foreign keys between services to
  # catch a mismatch, so a wrong id here just produces a showtime nothing
  # can resolve.
  screen=${entry%%:*}
  theater=${entry##*:}

  for day in 0 1 2; do
    date=$(date -d "+$day day" +%Y-%m-%d 2>/dev/null || date -v+"$day"d +%Y-%m-%d)
    for slot in "10:00:00|13:00:00" "14:00:00|17:00:00" "18:30:00|21:30:00"; do
      IFS='|' read -r start end <<EOF
$slot
EOF
      movie=$(printf '%s\n' "$MOVIE_IDS" | sed -n "$(( (i % MOVIE_COUNT) + 1 ))p")
      i=$((i+1))
      [ -z "$movie" ] && continue
      code=$(post /api/showtimes "{\"movieId\":$movie,\"theaterId\":$theater,\"screenId\":$screen,\"showDate\":\"$date\",\"startTime\":\"$start\",\"endTime\":\"$end\"}")
      [ "$code" = "201" ] || echo "  ! showtime $date $start -> $code $(head -c 100 /tmp/seed_out)"
    done
  done
done
echo "  $(curl -s "$GW/api/showtimes" | grep -o '"showtimeId"' | wc -l) showtimes now in the catalogue"

# ----------------------------------------------------------------- done --

echo
echo "Catalogue now holds:"
echo "  movies:    $(curl -s "$GW/api/movies"    | grep -o '"movieId"'    | wc -l)"
echo "  theaters:  $(curl -s "$GW/api/theaters"  | grep -o '"theaterId"'  | wc -l)"
echo "  screens:   $(curl -s "$GW/api/screens"   | grep -o '"screenId"'   | wc -l)"
echo "  seats:     $(curl -s "$GW/api/seats"     | grep -o '"seatId"'     | wc -l)"
echo "  showtimes: $(curl -s "$GW/api/showtimes" | grep -o '"showtimeId"' | wc -l)"
echo
echo "Admin login for the UI:  $ADMIN / password123"
