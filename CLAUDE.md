# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 4 / Spring Cloud microservice system for movie theater booking: twelve independent Maven projects under `D:\Movie-Booking-System` plus a React client in `movie-frontend/`. The root `pom.xml` is a **packaging-`pom` aggregator only** — each module's parent is `spring-boot-starter-parent`, so it inherits nothing from the root. Each service owns its dependency versions, its MySQL schema and its Maven wrapper.

One git repo (branch `master`). This file holds the rules; the reasons live in `docs/project-overview.md` (the system, the saga and payment rationale, "Lessons learned") and `docs/service-communication.md` (RestClient, Eureka, timeouts, breakers). Keep them in step with this file when behaviour changes.

**Repo hygiene:** `.idea/` is committed (8 files), so IntelliJ setting changes such as the Lombok annotation-processor profiles in `.idea/compiler.xml` show up in diffs. The root `.env` is gitignored and holds real secrets (`JWT_SECRET`, `BAKONG_*`); don't print or copy it — `.env.example` shows the shape.

## Do not break these

Each of these fails silently or reintroduces double-booking. Later sections add only what this list leaves out.

- **Seat reserve is a conditional UPDATE** (`SeatRepository.compareAndSetStatus`), never load-check-save. `SeatReservationConcurrencyTest` guards it.
- **`BookingServiceImpl.createBooking` is not `@Transactional`.** The reservation is a remote call; the compensating seat release in a `catch` replaces rollback.
- **Payment state changes are conditional UPDATEs too** (`PaymentRepository.markPaidIfPending` / `compareAndSetStatus`); only the caller that changes the row confirms the booking.
- **Circuit-breaker fallbacks rethrow the original exception.** They never invent a result and never wrap it (`run()` without a fallback turns every error into a 500).
- **Inject the load-balanced `RestClient.Builder` with `@LoadBalanced`** on the constructor parameter. By type alone you get the plain `@Primary` builder and `http://SEAT-SERVICE` doesn't resolve.
- **CORS lives in api-gateway's `CorsConfig` only**, at `Ordered.HIGHEST_PRECEDENCE`. No `@CrossOrigin` downstream.
- **`@EnableScheduling` on `PaymentServiceApplication`** is what drives the Bakong sweep.
- **Status fields and `Role` are enums** (`@Enumerated(EnumType.STRING)`), never raw strings.
- **Use `spring-boot-starter-flyway`**, never bare `flyway-core` (Boot 4 then silently skips migrations). Add migrations as a new `V<n>`; don't edit applied ones.

## Commands

There is **no wrapper at the root**, only inside each module. System Maven 3.9.11 is on `PATH`.

**`java` and `JAVA_HOME` are JDK 26, but every module targets Java 21.** A JDK-26 build doesn't fail with a toolchain error. Lombok's processor just stops running, and you get a wall of `cannot find symbol: method getUsername()` from correct code. Use the JDK 21 that is installed. The wrappers read `JAVA_HOME` too, and the variable lasts only for the shell that set it, so **set it in every separate command** (from Bash: `JAVA_HOME="C:/Users/MSI/.jdks/ms-21.0.12" ./mvnw ...`):

```powershell
$env:JAVA_HOME = "C:\Users\MSI\.jdks\ms-21.0.12"; mvn clean install   # all 12 modules
mvn clean test                   # all context-load tests
mvn -pl auth-service install     # one module
```

Per module, prefer its wrapper (`.\mvnw.cmd` in PowerShell, `./mvnw` from Bash):

```powershell
$env:JAVA_HOME = "C:\Users\MSI\.jdks\ms-21.0.12"
cd auth-service
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test -Dtest=AuthServiceApplicationTests               # one class
.\mvnw.cmd test -Dtest=AuthServiceApplicationTests#contextLoads  # one method
```

**Flyway without booting the app:** all nine data services have `flyway-maven-plugin` (credentials `root`/`root` via `db.user` / `db.password`). **Run it inside a data-service module.** From the root it stops at discovery-server, which has no datasource, with `Unable to connect to the database`:

```powershell
cd payment-service
mvn flyway:info      # what has been applied
mvn flyway:repair    # realign checksums after editing an applied migration
```

The plugin URL is hard-coded to **`localhost:3306`** (the local MySQL). For the compose stack, override it, e.g. `"-Dflyway.url=jdbc:mysql://localhost:3307/booking_db"`. Otherwise you read a different database. Keep the quotes in PowerShell: it splits an unquoted `-Dname.with.dots=value` at the first dot.

**Always `mvn clean` after renaming or deleting a migration.** `target/classes/db/migration` isn't pruned, so a stale `V2__old_name.sql` sits beside its replacement and Flyway sees two V2s.

**Lint:** none for the Java modules. The frontend uses **`oxlint`** (`npm run lint`). Don't add ESLint alongside it.

### Tests

**Every test but one is a `@SpringBootTest` `contextLoads` smoke test.** It boots the full context, so a data service needs its database on the local MySQL (3306); discovery-server, api-gateway and admin-service have none. Eureka doesn't have to be up: the client only logs `Connection refused` and the test still passes. `mvn test` is really a check that all twelve services can start.

**Booting the context runs Flyway**, so a test run applies any pending migration to the local database for real.

The exception is **`SeatReservationConcurrencyTest`** in seat-service. It needs **Docker** (Testcontainers MySQL, Eureka client off), not the local MySQL. It releases sixteen threads against one AVAILABLE seat, and exactly one must win:

```powershell
cd seat-service; .\mvnw.cmd test -Dtest=SeatReservationConcurrencyTest
```

If you touch the reservation path, deliberately swap in a read-then-write once and confirm this test goes red.

Testcontainers 2.x renamed its artifacts (`testcontainers-mysql`, not `mysql`), and `MySQLContainer` is now in `org.testcontainers.mysql` and no longer generic. The Boot BOM manages the versions, so declare none.

## Running the system

### Docker

```powershell
docker compose up --build        # first build is slow
docker compose up -d --wait      # returns once every container is healthy
docker compose down [-v]         # -v also wipes the MySQL volume
```

Compose starts MySQL, then Eureka, then the eleven services. `docker/mysql/init.sql` only creates the nine databases; Flyway creates every table at service startup. The build stage pins `maven:3.9-eclipse-temurin-21`, which avoids the JDK problem above.

Differences from a local run:

- **MySQL is on host port `3307`** (the local MySQL holds 3306). Inside the network it's `mysql:3306`.
- **Only api-gateway (8083), Eureka (8761) and MySQL (3307) are published.** Publishing a business service exposes its `permitAll()` chain and skips authentication. Add a `ports:` entry only deliberately, for probing.
- **Config comes from environment variables** (`SPRING_DATASOURCE_URL`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`, `EUREKA_INSTANCE_PREFER_IP_ADDRESS`). The checked-in properties keep pointing at `localhost`. Copy `.env.example` to `.env` to override `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, `BAKONG_*`. Compose also reads `CORS_ALLOWED_ORIGINS` and `TZ` from `.env`, though the template doesn't list them.
- **payment-service alone gets `TZ`** (default `Asia/Phnom_Penh`). A QR's `expires_at` is computed on it; a local run uses the host's zone.

### Verification scripts (against a running stack)

Run each from the repo root with `bash scripts/<name>.sh`. They need `curl`, and all but `frontend-ready-check.sh` go through `docker compose exec` (MySQL as `root`/`root`), so they work against the compose stack only, not a local run. Three disrupt it: `resilience-check.sh` stops user-service, `health-check.sh` stops MySQL, and `clean-test-data.sh` deletes every booking, payment and user account.

| Script | Proves |
| --- | --- |
| `scripts/smoke-test.sh` | end to end through the gateway: 400 with field names, 401, public catalogue reads |
| `scripts/resilience-check.sh` | timeouts and the circuit breaker actually fire (stops user-service) |
| `scripts/authz-check.sh` | USER can't do ADMIN things, including the demotion lag under Authorization |
| `scripts/health-check.sh` | all 12 health endpoints; stops MySQL and expects booking-service `DOWN` and the gateway `UP` |
| `scripts/frontend-ready-check.sh` | `userId` and CORS via a real preflight (curl ignores CORS, so the others can't catch it) |
| `scripts/seed-demo-data.sh` | fills an empty catalogue and prints an admin login |
| `scripts/clean-test-data.sh` | removes test leftovers; recreates `admin` / `demo` (both `password123`) |

**Wait about 40 seconds after `up`.** Until every service has registered with Eureka the gateway answers 500, and that isn't a failure.

`seed-demo-data.sh` is **additive, not idempotent**: each run creates new theaters and screens, because seat numbers are unique per screen. It promotes its own account in `auth_db`, since the first admin can't be made through the API. `clean-test-data.sh` deletes in dependency order (bookings/payments, release seats, screens, theaters, movies) because nothing cascades across services.

`MovieStatus` is `NOW_SHOWING`, `COMING_SOON` or `ARCHIVED`, not `UPCOMING`. Jackson rejects unknown values as a 400.

### Locally (a subset)

Start in order: **MySQL** on 3306 (`root`/`root`, the nine databases below), **discovery-server** on 8761, **api-gateway** on 8083, then any services. A service must be registered before callers can reach it (about 30s).

| Service | Port | Database | Gateway route |
| --- | --- | --- | --- |
| discovery-server | 8761 | — | — |
| auth-service | 8082 | `auth_db` | `/api/auth/**` |
| api-gateway | 8083 | — | — |
| theater-service | 8084 | `theater_db` | `/api/theaters/**` |
| screen-service | 8085 | `screen_db` | `/api/screens/**` |
| showtime-service | 8086 | `showtime_db` | `/api/showtimes/**` |
| seat-service | 8087 | `seat_db` | `/api/seats/**` |
| booking-service | 8088 | `booking_db` | `/api/bookings/**` |
| payment-service | 8089 | `payment_db` | `/api/payments/**` |
| user-service | 8090 | `user_db` | `/api/users/**` |
| movie-service | 8091 | `movie_db` | `/api/movies/**` |
| admin-service | 8093 | — | `/api/admin/**` |

Every module is configured in `application.properties` **except api-gateway, which uses `application.yml`** (its route table is a nested list). Don't add a `.properties` beside it.

### Frontend (`movie-frontend/`)

```bash
npm install && npm run dev   # http://localhost:5173
npm run build | lint | preview
```

- **Not in `docker-compose.yml`.** It always runs via npm against the gateway named by `VITE_API_URL` (default `http://localhost:8083`). Read `movie-frontend/README.md` before changing the client.
- **API calls go through `src/api/client.js`** (plain `fetch`). It attaches the bearer token and throws an `ApiError` that keeps the status. On a 401 it retries once through `/api/auth/refresh`, and concurrent 401s share one in-flight refresh. Access tokens last 15 minutes, so this is routine. `axios`, `@tanstack/react-query` and `zustand` are in `package.json` but unused.
- **Keep per-status handling:** 401 refresh-then-sign-in, 403 hide the control, **409 reload the seat grid** (losing a seat race is normal), 502/503 retry.
- **Posters come from TMDB in the browser** (`src/api/useTMDB.js`, `VITE_TMDB_TOKEN` in `movie-frontend/.env`, a v4 Bearer token, not a v3 key). It degrades silently to `null`, so "no images" almost always means the token is unset or wrong.
- **Tailwind v4 via `@tailwindcss/vite`**, configured CSS-first in `src/index.css`. Don't add a `tailwind.config.js`.
- **`components/HealthTab.jsx` and the health helper in `api/endpoints.js`** hard-code `localhost:8083` / `:8761` and ignore `VITE_API_URL`.
- **`payments.createBakong` shares one in-flight promise per booking** (`api/endpoints.js`), so StrictMode's double effect sends one POST. The server enforces the same rule; don't drop either side.
- **CORS allows only `localhost` ports 5173, 5174 and 3000** (override with `CORS_ALLOWED_ORIGINS`; recreate the container, no rebuild). Any other dev port is blocked until listed. The default list is written three times: `cors.allowed-origins` in the gateway's `application.yml`, the `@Value` fallback in `CorsConfig`, and `docker-compose.yml`. Change all three.
- **Known gaps:** no ownership checks, admin can't be granted from the UI, and no seat hold timer. A selected seat is booked immediately, so an abandoned `PENDING` booking holds it until cancelled.

## Architecture

### The booking and payment saga

```
POST /api/bookings
  booking -> user-service  GET /api/users/{userId}     no such user -> 400, nothing reserved
  booking -> seat-service  PUT /api/seats/{id}/reserve  AVAILABLE -> BOOKED, booking PENDING (201)
                                                        not AVAILABLE -> 409, no booking row

POST /api/payments/bakong {bookingId}
  payment -> booking  GET /api/bookings/{id}   must be PENDING and owned by X-Auth-UserId (else 403)
  KHQR built (NBC SDK) for booking.totalAmount -> payment PENDING (qr_string, md5, expires_at; unexpired QR re-used)

GET /api/payments/{id}/bakong/check  (browser, every 3s)  +  BakongPaymentSweeper (every 30s)
  payment -> Bakong Open API  POST /v1/check_transaction_by_md5
    found, account/amount/currency match -> PAID (conditional UPDATE) -> booking PUT /confirm -> CONFIRMED
    not found, past expires_at + 30s     -> EXPIRED -> booking PUT /cancel -> seat released

PUT /api/payments/{id}/failed|cancel  (from PENDING)
PUT /api/payments/{id}/refund|paid    (ADMIN; refund from PAID)
  -> booking PUT /cancel|confirm; cancel releases the seat (BOOKED -> AVAILABLE)
```

The reasoning behind each rule below is in `docs/project-overview.md` §4.

**Booking rules:**

- Validate the user, then reserve the seat, then write the booking row. Don't reorder.
- **Release is idempotent** (`BOOKED -> AVAILABLE`, otherwise a no-op). `safeReleaseSeat` logs and swallows seat-service errors, so a seat may stay `BOOKED` for reconciliation.
- `cancelBooking` returns early on `CANCELLED`. `confirmBooking` refuses `CANCELLED` (400). `deleteBooking` releases the seat unless already `CANCELLED`. `updateBooking` never changes `seatId`.

**Payment rules** (Bakong is the source of truth for "paid"):

- **Amounts never come from a request body.** Booking price = seat `price`; payment charges the booking total. Only ADMIN can call `/paid`.
- **`bakong_hash` and `md5` are UNIQUE.** So is `live_qr_booking_id` (payment `V3`, a generated column, not mapped on the entity): at most one PENDING QR per booking. `createBakongPayment` catches the violation and returns the winner's QR, because concurrent creates (double click, StrictMode) are normal.
- **Money arriving is never undone.** If the confirm fails, the payment stays PAID with `booking_confirmed_at` NULL and the sweeper retries for 24h. A refused confirm (booking cancelled mid-payment) is logged for manual refund.
- **"Cannot check" isn't "unpaid":** a Bakong outage settles and expires nothing. Unset `BAKONG_API_TOKEN` / `BAKONG_ACCOUNT_ID` → `POST /api/payments/bakong` returns **503**. The token expires about every 90 days. The Open API may only answer Cambodian IPs. There is no sandbox; end-to-end testing means a real, small payment, or **`BAKONG_MOCK=true`**: no token needed, the Open API is never called, QRs are built for `cinebook@mock`, and `POST /api/payments/{id}/bakong/simulate` (404 when off; the checkout shows a "Simulate payment" button) settles through the normal `settle` → confirm path.
- `BakongPaymentSweeper` is the only scheduler in the repo. `bakong.sweep-interval-ms` sets both the initial delay and `fixedDelay` (keep it `fixedDelay`, not `fixedRate`).

### Layering (house style)

Packages under `com.example.<name>service`: `client`, `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `service`.

- **Controllers:** `@RestController` at `/api/<plural>`, returning `ResponseEntity<DTO>`. Dependencies come in through an **explicit constructor** (no `@Autowired`, no `@RequiredArgsConstructor`).
- **Services:** an interface plus an `Impl`. The `Impl` maps entity↔DTO in a private `mapToResponse`; there's no mapper library.
- **Repositories:** bare `JpaRepository` with derived queries, plus the `@Modifying` conditional updates.
- **Entities:** Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (never `@Data`), `@Column(name = "snake_case")`, `GenerationType.IDENTITY`.
- **DTOs:** `record`s with a `DTO` suffix, **except user-service and admin-service, which use POJOs** with hand-written getters and setters. Match the module you're in.

### Validation

Details and reasons are in `docs/project-overview.md` §7.

- Every `@RequestBody` is `@Valid`; add `spring-boot-starter-validation` to any new module that takes a body.
- **`@Size` matches the column width** (`VARCHAR(100)` → `@Size(max = 100)`). Widen both together.
- `status` on `SeatRequestDTO` / `MovieRequestDTO` stays nullable (`null` = default on create, unchanged on update).
- Cross-field rules are `@AssertTrue` methods that return `true` when either field is null (`ShowtimeRequestDTO.isEndAfterStartTime()`).
- Login and refresh DTOs check presence only.

### Errors

Each service has `exception/GlobalExceptionHandler` returning an `ErrorResponse` record (`timestamp`, `status`, `error`, `message`, `path`). Throw a typed exception, never a bare `RuntimeException`.

| Exception | Status |
| --- | --- |
| `ResourceNotFoundException` | 404 |
| `IllegalArgumentException`, `InvalidBookingReferenceException` (booking: userId doesn't exist) | 400 |
| `MethodArgumentNotValidException` (all violations joined into `message`), `HttpMessageNotReadableException` (bad JSON or enum), `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` | 400 |
| `InvalidCredentialsException` (auth) | 401 |
| `PaymentForbiddenException` (payment: someone else's booking) | 403 |
| `DuplicateResourceException` (auth, user), `SeatNotAvailableException` (seat), `SeatUnavailableException` (booking), `PaymentStateException` (payment) | 409 |
| `RestClientException`, `BakongApiException` | 502 — a dependency answered badly |
| `CallNotPermittedException`, `BakongNotConfiguredException` | 503 — breaker open or Bakong unconfigured; retry later |
| anything else | 500 |

### Service-to-service calls and resilience

Details are in `docs/service-communication.md`. The four callers (auth, admin, booking, payment) each have `config/RestClientConfig` and `config/ResilienceConfig`.

- Current calls: auth → user (profile on register), admin → user (listing), booking → seat (reserve/release), booking → user (validate userId), payment → booking (get/confirm/cancel).
- **Timeouts (connect 2s, read 3s) are only on the `@LoadBalanced` builder**, via `HttpClientSettings` from **`spring-boot-http-client`**, which each caller declares because the web starter doesn't include it. Boot 3's `ClientHttpRequestFactorySettings` is gone, so don't follow older examples. The `@Primary` plain builder is Eureka's and stays untimed.
- **`ignoreExceptions` includes `HttpClientErrorException`** (a 4xx means the dependency is healthy). booking-service also ignores `SeatUnavailableException` and `InvalidBookingReferenceException`.
- **`spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true` in all four**, so the call keeps its `ThreadLocal` context and doesn't get a second timeout.
- **payment → Bakong Open API is the only call leaving the cluster.** It uses a third builder, `bakongRestClientBuilder` (injected by `@Qualifier`), timed but *not* `@LoadBalanced`. "Transaction not found" is `Optional.empty()`, not an exception.

### Health

All twelve services expose actuator **`health` and `info` only**, with probes enabled (`/actuator/health/liveness|readiness`). Keep everything else closed, because the downstream chains are `permitAll()`. `show-details=always` is a development setting.

- Each image's `HEALTHCHECK` curls `/actuator/health` for `"status":"UP"`. `curl` is installed in the runtime stage only for this.
- `eureka.client.healthcheck.enabled=true` on all eleven clients, so a service whose database is gone is registered `DOWN` (**about 90 seconds** to propagate).
- **A service with a restrictive security chain must permit `/actuator/health`**, as auth-service does, or its container sits `unhealthy`.

### Schema ownership

Flyway owns the schema. Every data service sets `ddl-auto=validate`, so an entity that doesn't match its table fails at startup. Migrations are `src/main/resources/db/migration/V<n>__<desc>.sql`. When migrations don't apply, check for `spring-boot-starter-flyway` first.

- **Every data service except screen-service sets `baseline-on-migrate=true`.** On a database that already has tables, V1 is recorded as applied without running, so editing V1 has no effect there. Editing any applied migration breaks its checksum; `mvn flyway:repair` only if history really must change.
- The `V1` files reproduce the tables in the live MySQL, so fresh and existing databases converge.

### Cross-service data and identity

There are no cross-service foreign keys. A reference is an integer, and validating it takes a remote call.

- **booking-service validates `seatId` (the reserve) and `userId` (`GET /api/users/{id}`).** A user-service outage blocks bookings with 502 rather than failing open. `updateBooking` re-checks only when the owner changes.
- **Taken on trust:** `Booking.showtimeId`, `Seat.screenId`, and `Showtime.movieId`/`theaterId`/`screenId`. The legacy `POST /api/payments` still accepts a client amount, but nothing it creates can reach PAID without an ADMIN.
- **There are two user tables.** `auth_db.users` holds credentials; `user_db.users` holds profiles, and `Booking.userId` refers to it. `auth_db.users.profile_id` (auth `V3`, nullable) links them. The link travels as a `userId` token claim, which the gateway forwards as **`X-Auth-UserId`**, and it's returned by register, login and profile. `login` backfills a missing `profile_id` via `GET /api/users/email/{email}` and never throws. auth_db must never read user_db's tables directly.
- **`AuthServiceImpl.register` is a `@Transactional` dual write**, so a failed user-service call rolls back the credential row.

### Security

**The gateway authenticates** (api-gateway's `JwtAuthenticationFilter`):

- **No token needed:** `/api/auth/register|login|refresh`, `/actuator/**`, and GET/OPTIONS on `/api/movies|theaters|screens|showtimes|seats/**`.
- **Everything else needs a valid access token** (refresh tokens are rejected). The gateway forwards the caller as `X-Auth-Username`, `X-Auth-Role` and `X-Auth-UserId`, **after stripping any inbound `X-Auth-*`**. Downstream services trust these headers only because of that stripping.
- **The gateway and auth-service share `jwt.secret`** (`${JWT_SECRET:<dev default>}`), so set it in both outside development.

**Downstream services run `permitAll()` chains** and rely on not being exposed. Nine modules include `spring-boot-starter-security` (admin, auth, booking, payment, screen, seat, showtime, theater, user), and each needs its `config/SecurityConfig`. Without it, Boot falls back to HTTP Basic, and every gateway call gets a 401 that looks like a gateway bug. movie-service has no security starter; add the bean if it ever gets one. auth-service is the only restrictive chain. It has `@EnableMethodSecurity`, and its filter grants `ROLE_<role>` from the **persisted row**, not the token claim.

### Authorization

The roles are `Role.USER` and `Role.ADMIN`, backed by the `chk_users_role` CHECK constraint (auth `V2`). **The gateway decides in `requiresAdmin`:**

| Request | Needs |
| --- | --- |
| `GET` on the catalogue | nothing |
| `POST`/`PUT`/`DELETE` on the catalogue | ADMIN |
| `/api/admin/**`, `/api/auth/users/**`, `GET /api/users`, `DELETE /api/users/**` | ADMIN |
| `PUT /api/payments/*/paid`, `/status`, `/refund` | ADMIN |
| bookings, other payment calls, `/api/auth/profile`, a single user by id | any valid token |

A wrong role is **403**, not 401.

- **Internal calls never pass through the gateway**, so gateway rules don't affect them: seat reserve/release, booking confirm/cancel from payment-service, and `POST /api/users` from auth-service. Check this before adding a rule; a path that looks like a user action may be an internal one.
- **`PUT /api/auth/users/{username}/role` is the only way to grant a role.** It has `@PreAuthorize("hasRole('ADMIN')")`, enforced by auth-service independently of the gateway. **The first admin needs SQL:** `UPDATE auth_db.users SET role = 'ADMIN' WHERE username = '...';`
- **The gateway only sees role changes on a new token.** A promotion needs a re-login, and a **demotion keeps gateway-level ADMIN for up to 15 minutes** (the access-token lifetime). `authz-check.sh` asserts this.
- **Ownership checks are mostly missing.** Any user can cancel any booking or edit any user by id. The exceptions are `POST /api/payments/bakong` and `POST /api/payments/{id}/bakong/simulate` (ADMIN exempt), which compare the booking's `userId` with `X-Auth-UserId`; copy that pattern.
- **`POST /api/auth/logout` revokes nothing.** There's no token store, so tokens stay valid until they expire (access 15 min, refresh 7 days).

### Version alignment

All twelve modules are on **Spring Boot 4.1.0 / Spring Cloud 2025.1.2**; keep new modules aligned. Each module imports the `spring-cloud-dependencies` BOM itself. Without it the Eureka client has no version and Maven can't read the project.

The only hand-pinned runtime dependency is **`kh.gov.nbc.bakong_khqr:sdk-java` `1.0.0.17`** in payment-service; no BOM manages it. The build-time `flyway-maven-plugin` (and its `flyway-mysql` dependency) is pinned to **11.14.1** in all nine data services, while Boot 4.1.0 runs Flyway **12.4.0** at startup; bump them together if `flyway:info`/`repair` ever disagrees with what the app applied. `spring-boot-starter-web` and `-webmvc` are both used, which is harmless.
