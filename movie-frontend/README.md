# movie-frontend

React + Vite client for the Movie Booking System.

## Running

The backend must be up first — the frontend talks only to the gateway:

```bash
cd ..                       # project root
docker compose up -d --wait
# then wait ~40s: services register with Eureka after the containers are
# healthy, and until they do the gateway answers 500

cd movie-frontend
npm install
npm run dev                 # http://localhost:5173
```

`VITE_API_URL` in `.env` points at the gateway (`http://localhost:8083`).
Port **5173 is already in the gateway's CORS allow-list**; if you run the dev
server on a different port, add it to `cors.allowed-origins` in
`api-gateway/src/main/resources/application.yml` or the browser will block
every request.

## How it is put together

- `src/api/client.js` — the only place `fetch` is called. Attaches the
  bearer token, parses errors into `ApiError` (keeping the status, which the
  UI reacts to), and retries once through `/api/auth/refresh` on a 401.
  Access tokens last 15 minutes, so that refresh is a normal occurrence.
- `src/api/endpoints.js` — every backend path in one place.
- `src/auth/AuthContext.jsx` — session state, seeded from `localStorage` so a
  reload does not sign you out.
- `src/pages/` — one file per screen.

## Things the backend makes you handle

**`userId` comes from the token, never from a form.** Login returns
`{ accessToken, refreshToken, username, userId, role }`; `userId` is the id
of the profile row in user-service and is what `Booking.userId` refers to.

**A 409 on booking is normal.** Two people can have the same seat on screen;
seat-service resolves it with a conditional UPDATE and the loser gets a 409.
`Book.jsx` treats it as an expected outcome and reloads the grid.

**Status codes mean different things** and the UI should not flatten them:

| Code | Meaning | Response |
| --- | --- | --- |
| 401 | token missing or expired | refresh, else sign in |
| 403 | valid token, wrong role | hide the control |
| 409 | seat already taken | reload the grid |
| 502 | a dependency is unreachable | retry |
| 503 | circuit breaker open | retry shortly |

**Signing out clears local storage only.** The backend's logout endpoint
revokes nothing — the tokens stay valid until they expire.

## Known gaps

- **No ownership enforcement on the server.** `My bookings` filters by
  calling `/api/bookings/user/{userId}`, but nothing stops a crafted request
  reading someone else's. The groundwork exists (`X-Auth-UserId` reaches
  booking-service from the signed token); the check itself is not written.
- **Admin cannot be granted from the UI.** The first admin is made in the
  database, then `PUT /api/auth/users/{username}/role` can promote others.
  A promoted user must sign in again — the gateway reads the role from the
  token, not the database.
- **Seats have no hold timer.** Selecting a seat books it immediately; an
  abandoned PENDING booking holds it until someone cancels.
