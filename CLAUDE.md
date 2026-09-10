# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 4 / Spring Cloud microservice system for movie theater booking. Twelve independent Maven projects sit side by side under `D:\Movie-Booking-System`. The root `pom.xml` is a **packaging-`pom` aggregator only** — the child modules declare `spring-boot-starter-parent` as their parent, not this aggregator, so they inherit nothing from it. Each service owns its own dependency versions, its own MySQL schema, and its own Maven wrapper.

Not a git repository. Each module has a `.gitignore`, but nothing is initialized or tracked.

## Commands

There is **no wrapper at the root** — only inside each module. System Maven 3.9.11 is on `PATH` and runs on JDK 21, so aggregate builds work from the root:

```powershell
mvn clean install            # build all 12 modules (root aggregator)
mvn clean test               # all 12 context-load tests
mvn -pl auth-service install # build one module
```

Per module, prefer its wrapper (`.\mvnw.cmd` in PowerShell, `./mvnw` from the Bash tool):

```powershell
cd auth-service
.\mvnw.cmd spring-boot:run                                       # run the service
.\mvnw.cmd clean package                                         # build the jar
.\mvnw.cmd test                                                  # all tests
.\mvnw.cmd test -Dtest=AuthServiceApplicationTests               # one class
.\mvnw.cmd test -Dtest=AuthServiceApplicationTests#contextLoads  # one method
```

`auth-service`, `movie-service` and `seat-service` carry the `flyway-maven-plugin`, so migration state can be inspected and repaired without booting the app. Credentials default to `root`/`root` via the `db.user` / `db.password` properties:

```powershell
mvn flyway:info      # what has been applied
mvn flyway:repair    # realign checksums after editing an applied migration
```

No linter or formatter is configured for the twelve Java modules. The frontend has one: **`oxlint`**, via `npm run lint` in `movie-frontend/`. There is no ESLint config despite the Vite React scaffold normally shipping one — do not add ESLint alongside it.

**`java` on `PATH` is JDK 26 and so is `JAVA_HOME`; every module targets Java 21.** A JDK-26 build does not fail with a toolchain error — Lombok's annotation processor stops running and you get a wall of `cannot find symbol: method getUsername()` from code that is perfectly correct. `C:\Users\MSI\.jdks\ms-21.0.12` is present; prefix aggregate builds with it:

```powershell
$env:JAVA_HOME = "C:\Users\MSI\.jdks\ms-21.0.12"; mvn clean install
```

The Docker build below sidesteps this entirely — its build stage pins `maven:3.9-eclipse-temurin-21`.

**With one exception, every test is a `@SpringBootTest` `contextLoads` smoke test.** They boot the full context, so a data service's test needs its MySQL database *and* Eureka reachable or it fails. `mvn test` is really an integration check that all twelve services can start.

The exception is **`SeatReservationConcurrencyTest`** in seat-service, which is the only test asserting behaviour:

```powershell
cd seat-service
.\mvnw.cmd test -Dtest=SeatReservationConcurrencyTest
```

It needs **Docker running**, but *not* the local MySQL or Eureka — it starts its own MySQL through Testcontainers and switches the Eureka client off. Sixteen threads are released simultaneously against one AVAILABLE seat and exactly one must win.

**Do not "simplify" `reserveSeat` into a read-then-write.** That is the whole point of the test, and it has been verified to catch it: replacing `compareAndSetStatus` with load-check-save makes **12 of the 16 callers** believe they reserved the same seat. A test that never fails is decoration, so if you change the reservation path, mutate it deliberately once and confirm this test goes red.

Testcontainers artifacts are **renamed in 2.x** (`testcontainers-mysql`, not `mysql`) and `MySQLContainer` moved to `org.testcontainers.mysql` and is no longer generic. The Boot BOM manages the versions, so declare none.

**Always `mvn clean` after renaming or deleting a migration.** `target/classes/db/migration` is not pruned by an incremental build, so a deleted `V2__old_name.sql` lingers beside its replacement and Flyway sees two migrations with version 2.

## Running the system

### With Docker (the whole system)

```powershell
docker compose up --build      # first build is slow; images cache after that
docker compose up -d --wait    # returns only once every container reports healthy
docker compose down            # stop, keep the data
docker compose down -v         # stop and wipe the MySQL volume

bash scripts/smoke-test.sh        # end-to-end check once the gateway answers
bash scripts/resilience-check.sh  # proves the timeouts and breaker actually work
bash scripts/authz-check.sh       # proves USER cannot do ADMIN things
bash scripts/health-check.sh      # proves /actuator/health reflects reality
bash scripts/frontend-ready-check.sh  # proves userId + CORS work for a browser
bash scripts/seed-demo-data.sh    # fills an empty catalogue so the UI has content
bash scripts/clean-test-data.sh   # removes what the test suites left behind
```

`scripts/smoke-test.sh` drives the stack through the gateway the way a real client does — bad bodies rejected as 400 with the offending fields named, unauthenticated calls 401, catalogue reads public, valid bodies still accepted. **Give it ~40 seconds after `up`**: until every service has registered with Eureka the gateway answers 500, which looks like a failure and is not one.

`docker-compose.yml` starts MySQL, waits for it to actually accept connections, starts Eureka, waits for that, then starts the eleven services. `docker/mysql/init.sql` creates the nine databases and nothing else — every table in them is still Flyway's, applied at each service's startup.

Three things differ from a local run and will bite otherwise:

- **MySQL is published on `3307`, not `3306`**, because the local MySQL this project was developed against normally holds 3306. Inside the network it is still `mysql:3306`.
- **Only `api-gateway` (8083), Eureka (8761) and MySQL (3307) are published.** The business services are reachable only on the internal network — publishing one would expose its `permitAll()` chain directly and skip authentication entirely. Add a `ports:` entry deliberately when you need to probe one.
- **Configuration comes from environment variables, not the property files.** Compose sets `SPRING_DATASOURCE_URL`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` and `EUREKA_INSTANCE_PREFER_IP_ADDRESS`, so the checked-in config keeps pointing at `localhost` and a local run is unaffected. Copy `.env.example` to `.env` to override `MYSQL_ROOT_PASSWORD` and `JWT_SECRET`.

### Locally (a subset of services)

Start in this order; everything assumes `localhost`:

1. **MySQL** on `3306`, user `root` / password `root`, one database per service (`auth_db`, `user_db`, `movie_db`, `theater_db`, `screen_db`, `showtime_db`, `seat_db`, `booking_db`, `payment_db`).
2. **discovery-server** (Eureka) on `8761` — every other service registers against `http://localhost:8761/eureka/`.
3. **api-gateway** on `8083` — the single entry point, and where authentication is enforced.
4. Any subset of the business services.

| Service | Port | Database | Gateway route |
| --- | --- | --- | --- |
| discovery-server (Eureka) | 8761 | — | — |
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
| admin-service | 8093 | — (no datasource) | `/api/admin/**` |

Service-to-service calls resolve through Eureka by service id (`http://SEAT-SERVICE`), so a service must be registered before its callers can reach it — allow ~30s after startup.

Every module configures itself in `src/main/resources/application.properties` **except `api-gateway`, which uses `application.yml`** — its route table is a nested list that does not express well as flat properties. Creating an `application.properties` next to it would not fail; the YAML would just keep winning for anything the two both define.

### Sample data

`scripts/seed-demo-data.sh` fills an empty catalogue — 6 movies, 2 theaters, 4 screens, 160 seats (rows A-E x 1-8) and 36 showtimes. It registers its own account and promotes it in auth_db, because catalogue writes are ADMIN-only and the first admin cannot be made through the API. It prints that login at the end.

Additive, not idempotent: each run creates new theaters and screens. That is deliberate — seat numbers are unique per screen, so re-running would 409 on every seat if it reused them.

**`MovieStatus` is `NOW_SHOWING`, `COMING_SOON`, `ARCHIVED`.** Not `UPCOMING`; Jackson rejects an unknown value as a 400 before validation runs.

The test suites each register accounts and create throwaway theaters and movies, so a few runs leave the catalogue full of `Smoke Cinema` and `Authz Cinema`. `scripts/clean-test-data.sh` removes them and recreates two known logins, `admin` and `demo` (both `password123`), through the API so the passwords are hashed and each gets a linked profile.

It deletes in dependency order — bookings and payments first, then the seats they held are released, then screens whose theater is going, then the theaters and movies. **Nothing cascades on its own**: there are no foreign keys between services, so deleting a theater and stopping there leaves screens pointing at a theater that does not exist.

### The frontend

`movie-frontend/` is a React + Vite client, and the only Node project here — it is deliberately outside the Maven aggregator.

```bash
cd movie-frontend
npm install
npm run dev       # http://localhost:5173
npm run build     # production bundle into dist/
npm run lint      # oxlint
npm run preview   # serve the built bundle
```

**The frontend is not in `docker-compose.yml`.** `docker compose up` brings up MySQL and the twelve Java services only; there is no frontend image and nothing serves `dist/`. The client is always run with npm, against whichever gateway `VITE_API_URL` names.

`movie-frontend/README.md` is worth reading before changing the client. Besides the status-code contract below it records three gaps: no server-side ownership check, admin cannot be granted from the UI, and **seats have no hold timer** — selecting a seat books it immediately, so an abandoned `PENDING` booking holds that seat until someone cancels it.

It talks **only to the gateway** (`VITE_API_URL`, default `http://localhost:8083`). Port 5173 is already in `cors.allowed-origins` (in `api-gateway/src/main/resources/application.yml`); a dev server on any other port is blocked by the browser until that list is changed.

All `fetch` calls go through `src/api/client.js`, which attaches the bearer token, turns failures into an `ApiError` that **keeps the status code**, and retries once through `/api/auth/refresh` on a 401 — access tokens last 15 minutes, so that is routine rather than exceptional. Concurrent 401s share one in-flight refresh, otherwise four parallel requests start four refreshes and the last three present an already-rotated token.

The UI reacts to status codes individually and should keep doing so: 401 refresh-then-sign-in, 403 hide the control, **409 reload the seat grid** (losing the race for a seat is a normal outcome, not an error), 502/503 retry.

## Architecture

### The booking flow

This is the part worth understanding before changing anything. The eleven services are not independent CRUD apps; three of them implement a saga with a compensating action.

```
POST /api/bookings
  booking-service -> user-service  GET /api/users/{userId}
      no such user -> 400, nothing is reserved
  booking-service -> seat-service  PUT /api/seats/{id}/reserve
      seat AVAILABLE -> BOOKED, booking written as PENDING (201)
      seat not AVAILABLE -> 409, no booking row is written

POST /api/payments                        payment PENDING
PUT  /api/payments/{id}/paid
  payment-service -> booking-service  PUT /api/bookings/{id}/confirm
      booking PENDING -> CONFIRMED

PUT  /api/payments/{id}/failed|cancel|refund
  payment-service -> booking-service  PUT /api/bookings/{id}/cancel
      booking -> CANCELLED, and booking-service releases the seat
      seat BOOKED -> AVAILABLE
```

Three properties hold this together, and breaking any of them reintroduces double-booking:

- **The reserve is a conditional UPDATE, not a read-then-write.** `SeatRepository.compareAndSetStatus` issues `UPDATE ... WHERE seat_id = ? AND status = ?` and checks the affected-row count. Zero rows means the caller lost the race. Loading the seat, checking `status` in Java, then saving would let two concurrent bookings both observe AVAILABLE.
- **The user is validated before the seat is reserved.** The check has no side effect, so failing it costs nothing; doing it after the reservation would mean holding and releasing a seat for a request that was never going to succeed.
- **The seat is reserved before the booking row is written.** A failed reservation therefore leaves nothing behind. If the insert then fails, `BookingServiceImpl` releases the seat in a `catch` block.
- **Release is idempotent** (`BOOKED -> AVAILABLE`, a no-op otherwise) so a retried compensating call cannot fail, and `safeReleaseSeat` swallows and logs seat-service errors — a cancellation must not be undone just because seat-service is briefly down. The cost is that a seat can be left stuck `BOOKED`; that is logged for reconciliation.

`createBooking` is deliberately **not** `@Transactional` — the reservation is a remote call and cannot join a local transaction. The compensating release is what substitutes for rollback.

Three more rules in `BookingServiceImpl` follow from the same reasoning:

- `cancelBooking` returns early when the booking is already `CANCELLED`, so a retried compensating call from payment-service is harmless; `confirmBooking` refuses a `CANCELLED` booking outright (400).
- `deleteBooking` also releases the seat, unless the booking was already `CANCELLED` (its seat was released then). A hard delete that skipped this would leak the seat permanently — there is no row left to reconcile from.
- `updateBooking` will not change `seatId`. Moving a booking to a different seat is a release plus a reserve, i.e. a cancel and re-book, not a field edit.

### Layering (the house style)

Every service repeats the same package layout under `com.example.<name>service`: `client` (where it calls other services), `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `service`.

- **Controllers** — `@RestController` at `/api/<plural>`, return `ResponseEntity<DTO>`, dependencies via an **explicit constructor** (no `@Autowired`, no `@RequiredArgsConstructor`).
- **Services** — an interface plus a separate `Impl`. The `Impl` also owns entity↔DTO mapping via a private `mapToResponse`; there is no mapper library.
- **Repositories** — bare `JpaRepository` interfaces, derived query methods, plus the one `@Modifying` query in `SeatRepository`.
- **Entities** — Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (never `@Data`), snake_case columns via `@Column(name = ...)`, `GenerationType.IDENTITY` ids.
- **DTOs** — Java `record`s with a `DTO` suffix everywhere except **user-service and admin-service, which use plain POJOs with hand-written getters/setters**. Match the surrounding module rather than converting one to the other.
- **Status fields are enums**, mapped `@Enumerated(EnumType.STRING)`: `SeatStatus`, `BookingStatus`, `PaymentStatus`, `MovieStatus`. Do not reintroduce raw `String` statuses — the saga's correctness depends on these values being closed sets.

### Validation

Every `@RequestBody` is `@Valid`, and the request DTOs carry Jakarta Bean Validation constraints. Nine services have `spring-boot-starter-validation`; without that starter the annotations compile and nothing enforces them, so add it when you add a module with a request body.

- **`@Size` caps mirror the actual column widths** (`VARCHAR(100)` → `@Size(max = 100)`). The point is that an over-long value becomes a 400 naming the field instead of a 500 from the driver. If you widen a column in a migration, widen the constraint with it.
- **`status` on `SeatRequestDTO` and `MovieRequestDTO` must stay nullable.** Both service impls read `null` as "use the default" on create and "leave it alone" on update. Adding `@NotNull` there silently breaks both behaviours.
- **`BookingRequestDTO` is the one that matters most.** An invalid body that reaches `createBooking` costs a remote user check and a seat reservation before the insert fails, and the seat then needs releasing. Validating at the edge means the saga never starts.
- **Cross-field rules go in an `@AssertTrue` method on the record** — `ShowtimeRequestDTO.isEndAfterStartTime()` is the only one. It returns `true` when either time is null, because `@NotNull` already reports that and two messages for one omission is noise.
- Login and refresh DTOs are **presence-only**. Constraining a password's length at login would leak the registration policy to unauthenticated callers and lock out credentials predating a policy change.

### Errors

Every service has `exception/GlobalExceptionHandler` (a `@RestControllerAdvice`) returning a shared `ErrorResponse` record — `timestamp`, `status`, `error`, `message`, `path`. The mapping:

| Exception | Status |
| --- | --- |
| `ResourceNotFoundException` | 404 |
| `IllegalArgumentException` | 400 |
| `DuplicateResourceException` (auth, user) | 409 |
| `InvalidCredentialsException` (auth) | 401 |
| `SeatNotAvailableException` (seat) / `SeatUnavailableException` (booking) | 409 |
| `InvalidBookingReferenceException` (booking) | 400 — the body names a user that does not exist |
| `MethodArgumentNotValidException` | 400 — a `@Valid` body failed; every violation joined into `message` |
| `HttpMessageNotReadableException` | 400 — body unparseable (bad JSON, or a value outside an enum) |
| `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` | 400 — a query parameter is absent or the wrong type |
| `RestClientException` | 502 — this service is fine, a dependency is not |
| `CallNotPermittedException` (the 4 callers) | 503 — the circuit is open; we did not even try |
| anything else | 500 |

Throw the typed exception; never a bare `RuntimeException`.

502 versus 503 is a real distinction, not a cosmetic one: 502 means the dependency answered badly, 503 means the breaker refused to call it at all and the caller should retry later.

### Health and probes

Every one of the twelve services has `spring-boot-starter-actuator`, exposing **`health` and `info` only** — `env`, `beans`, `loggers` and `heapdump` stay closed, because these services run `permitAll()` chains and anything exposed is exposed to whatever can reach the port. `management.endpoint.health.probes.enabled=true` adds `/actuator/health/liveness` and `/actuator/health/readiness`.

Two things consume it, and both are the reason it exists:

- **Each image's `HEALTHCHECK`** curls its own `/actuator/health` and greps for `"status":"UP"`. `curl` is installed in the runtime stage purely for this — the base image has neither curl nor wget, and the `/dev/tcp` probe this replaced only proved the port was open, which is true long before the service can serve a request and stays true after its database has gone.
- **`eureka.client.healthcheck.enabled=true`** on all eleven clients, so the registry carries real health instead of "the process is running". A service whose database is gone is marked `DOWN` and callers stop being handed it.

**The Eureka status lags by roughly 90 seconds** — measured, not assumed. It takes a few 30s heartbeat cycles to propagate, so this protects against a sustained outage, not a brief blip. The circuit breakers cover the seconds in between; these two mechanisms are complementary, not redundant.

`show-details=always` is a development setting. The service ports are not published outside the compose network, but on a real deployment it tells anyone who can reach the port exactly which dependency is broken.

**auth-service needs `/actuator/health` explicitly permitted** in its `SecurityConfig`. It is the only service with a restrictive chain (`.anyRequest().authenticated()`), so it was the only one answering **403** to its own health check — which presents as a container stuck `unhealthy`, not as anything security-shaped. Any future service with a real filter chain needs the same two matchers.

Verify with `bash scripts/health-check.sh`. It checks all twelve endpoints, that Docker agrees, and then **stops MySQL** to confirm booking-service actually reports `DOWN` (`CannotGetJdbcConnectionException`) while api-gateway, which owns no datasource, stays `UP`. A health check that cannot go down is worse than none, because orchestration believes it.

### Resilience

The four services that call other services — auth, admin, booking, payment — each configure timeouts in `config/RestClientConfig` and a circuit breaker in `config/ResilienceConfig`.

**Timeouts are on the `@LoadBalanced` builder only** (connect 2s, read 3s, via `HttpClientSettings`). The `@Primary` plain builder is Eureka's and is deliberately left untimed. A `RestClient` with no timeout waits forever, so one hung dependency holds a request thread per caller until the pool is exhausted — that is how a slow service becomes a cascading outage.

`HttpClientSettings` lives in **`spring-boot-http-client`**, which the web starter does not pull in, so each of the four declares it. Boot 3's `ClientHttpRequestFactorySettings` is gone in Boot 4; do not follow older examples.

**No breaker anywhere has a fallback that invents a result.** Every fallback rethrows the original exception, and that is the whole design:

- Returning "assume the user exists" would make the check fail *open*, which is the same as no check.
- Returning "the seat was reserved" double-books it; returning "it was not" loses a sale.
- An empty user list in admin-service is indistinguishable from a real empty system, and someone would act on it.

Rethrowing also keeps the original type, so the mapping above still applies. Wrapping in `NoFallbackAvailableException` — what Spring Cloud does if you call `run()` with no fallback — would turn every one of those into a 500.

**`ignoreExceptions` is the setting most likely to be got wrong.** A 4xx means the dependency answered correctly and rejected *our* request, so `HttpClientErrorException` never counts as a failure. booking-service additionally ignores `SeatUnavailableException` and `InvalidBookingReferenceException`: losing the race for a seat is the *most common* outcome under load, and counting it would open the circuit exactly when the system is busy and working perfectly.

`spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true` in all four. Otherwise the guarded call is handed to Resilience4J's own pool to enforce a TimeLimiter, losing the request's `ThreadLocal` context and stacking a second timeout on the HTTP one.

Verify with `bash scripts/resilience-check.sh` against a running stack. Measured behaviour, with user-service stopped:

| | Result |
| --- | --- |
| 15 bookings for a nonexistent user | 15 × 400, circuit **stayed closed** |
| bookings 1-10 with user-service down | 502 in **~2020ms** each — the 2s connect timeout |
| bookings 11-20 | 503 in **~15ms** — refused without calling |
| after user-service returns | back to 400 business logic within ~10s |

That ~2020ms is the evidence the timeout is real, and the 135× drop is the evidence the breaker is.

### Schema ownership

Schema is Flyway-owned. Every data service sets `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate never creates or alters a table and an entity that does not match its table fails at startup. Migrations live in `src/main/resources/db/migration` as `V<n>__<description>.sql`.

**Boot 4 ships Flyway's auto-configuration in `spring-boot-starter-flyway`.** A bare `flyway-core` dependency puts the library on the classpath but Boot never runs the migrations — silently, with no warning. Four services were in that state and their databases had no `flyway_schema_history` at all; the tables existed only because someone had created them by hand. Every service now uses the starter. If migrations mysteriously do not apply, check this first.

Every service sets `spring.flyway.baseline-on-migrate=true` except screen-service. On a database that already has tables, Flyway baselines at version 1 and **records V1 as applied without running it** — so editing V1 will not touch an existing database, and changing an already-applied migration's content breaks its checksum. Add a `V2__*.sql` instead, or run `mvn flyway:repair` if you genuinely must rewrite history.

`V1` files are written to reproduce the tables that already exist in the running MySQL instance, so a fresh database converges on the same schema as the live one.

### Cross-service data

There are no foreign keys between services — a reference is just an integer, and validating it means a remote call.

booking-service validates both of its own references: `seatId` via the reserve call, and `userId` via `GET /api/users/{id}` on user-service. A user-service outage therefore blocks new bookings (502) rather than letting them through unchecked — booking-service cannot distinguish "no such user" from "cannot check right now", and failing open would defeat the point of validating. `updateBooking` only re-checks when the owner actually changes.

Still taken on trust: `Booking.showtimeId`, `Payment.bookingId`, `Seat.screenId`, and `Showtime.movieId`/`theaterId`/`screenId`.

### Service-to-service calls

Callers use `RestClient` against Eureka service ids. Every module that makes such a call defines **two** builder beans in `config/RestClientConfig`:

- `@Primary` **plain** builder — so Eureka's own HTTP client, which injects `RestClient.Builder` by type, does *not* go through Spring Cloud LoadBalancer;
- `@LoadBalanced` builder — for the service's own outbound calls.

**Inject the load-balanced one with the `@LoadBalanced` qualifier on the constructor parameter.** Injecting `RestClient.Builder` by type alone silently resolves to the `@Primary` plain builder, and `http://SEAT-SERVICE` then has no resolver behind it. This was a live bug in admin-service.

Current callers: `auth-service -> user-service` (profile creation on register), `admin-service -> user-service` (user listing), `booking-service -> seat-service` (reserve/release), `booking-service -> user-service` (userId validation), `payment-service -> booking-service` (confirm/cancel).

`AuthServiceImpl.register` is a dual write across two databases and is `@Transactional`, so a failure of the user-service call rolls the credential row back rather than leaving `auth_db` and `user_db` out of step.

### Security

**Authentication is enforced at the gateway**, in `api-gateway`'s `JwtAuthenticationFilter`:

- Public with no token: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/actuator/**`.
- Public to **read** only (GET/OPTIONS): `/api/movies/**`, `/api/theaters/**`, `/api/screens/**`, `/api/showtimes/**`, `/api/seats/**` — catalogue browsing. **Writing to those paths needs ADMIN**; see Authorization below.
- Everything else requires a valid **access** token (a refresh token is rejected).
- On success the caller is forwarded as `X-Auth-Username` / `X-Auth-Role`. **Any inbound `X-Auth-*` header is stripped first**, so a client cannot assert its own identity. Downstream services may trust these headers only because the gateway guarantees that.

The gateway and auth-service must share `jwt.secret`; both read `${JWT_SECRET:<dev default>}`, so set `JWT_SECRET` in both processes outside local development.

auth-service additionally validates tokens itself and has `@EnableMethodSecurity`, which `AuthController.updateRole` uses. Its `JwtAuthenticationFilter` grants `ROLE_<role>` **from the persisted user row, not the token claim**, so a revoked role takes effect without waiting for the access token to expire.

Downstream services keep `permitAll()` filter chains — they are not directly exposed and rely on the gateway. **This means running a service on its own port bypasses authentication entirely**, which is fine for local testing and not fine for deployment. admin-service needs its `SecurityConfig` `permitAll` bean specifically because it pulls in `spring-boot-starter-security`; without the bean Boot falls back to HTTP Basic with a generated password and `/api/admin/**` becomes unusable through the gateway.

### Identity across the two user tables

There are **two** user tables and they are not the same thing: `auth_db.users` holds credentials (PK `user_id`, plus `username`), `user_db.users` holds the profile (PK `user_id`) — and `Booking.userId` refers to the **user_db** one.

`auth_db.users.profile_id` is the link, added in `V3`. `AuthServiceImpl.register` captures the id user-service returns and stores it; before this the response was discarded, so an authenticated caller had a username and no way to name the user a booking was for. The booking flow was literally unreachable from a client.

It travels as a **`userId` claim on the access token**, which the gateway forwards as **`X-Auth-UserId`** (stripped on the way in, like the other `X-Auth-*` headers), and it is returned by `/api/auth/register`, `/api/auth/login` and `/api/auth/profile`.

`profile_id` is **nullable** — rows predating V3 have none. `login` backfills them through user-service's `GET /api/users/email/{email}` rather than the migration doing a cross-database `UPDATE`: auth_db must never read user_db's tables, even though they share a MySQL instance. That backfill never throws; a missing profile or a briefly-down user-service must not stop someone signing in.

**`X-Auth-UserId` is what ownership checks should be built on** when someone adds them — it is derived from a signed token, not from the request body, so unlike the `userId` in a request body it cannot be forged.

### CORS

`api-gateway`'s `CorsConfig`, and **nowhere else**. Two things there are load-bearing:

- **`Ordered.HIGHEST_PRECEDENCE`.** A browser's preflight `OPTIONS` carries no `Authorization` header, so `JwtAuthenticationFilter` would answer it 401, the browser would report a CORS error, and the real request would never be sent. The `CorsFilter` has to run first and answer the preflight itself.
- **Only at the gateway.** Adding CORS downstream too makes a response carry two `Access-Control-Allow-Origin` headers, which browsers reject.

Origins are explicit (`cors.allowed-origins` in `application.yml`, default the Vite and CRA dev ports) because `allowCredentials` cannot be combined with `*`. Override with `CORS_ALLOWED_ORIGINS`.

**curl ignores CORS entirely**, so every script in `scripts/` can pass against an API no browser can call. `scripts/frontend-ready-check.sh` is the one that checks it, using a real preflight.

### Authorization

Two roles, `Role.USER` and `Role.ADMIN` — a real enum in auth-service, `@Enumerated(EnumType.STRING)`, with a `chk_users_role` CHECK constraint added in `V2`. It was free text before, which meant a hand-typed `"Admin"` produced the authority `ROLE_Admin`: every `hasRole('ADMIN')` returned false and the account looked promoted while behaving exactly like a normal user. Nothing reported it. Keep it a closed set.

**The gateway decides, in `requiresAdmin`.** Everything else there stays as described above.

| Request | Needs |
| --- | --- |
| `GET` on the catalogue | nothing — still fully public |
| `POST`/`PUT`/`DELETE` on the catalogue | **ADMIN** |
| `/api/admin/**` | **ADMIN** |
| `/api/auth/users/**` (granting roles) | **ADMIN** |
| `GET /api/users` (the full listing) | **ADMIN** |
| `DELETE /api/users/**` | **ADMIN** |
| bookings, payments, `/api/auth/profile`, a single user by id | any valid token |

Wrong role is **403, not 401** — the caller proved who they are and a fresh token will not help.

**Seat reserve/release is deliberately not affected.** Those are service-to-service calls through Eureka that never traverse the gateway, so requiring ADMIN for `/api/seats/**` writes does not touch the booking saga. The same is true of `/api/bookings/{id}/confirm|cancel` from payment-service, and `POST /api/users` from auth-service on register. Check this before adding a gateway rule: a path that looks like a user action may be an internal one.

`PUT /api/auth/users/{username}/role` is the only place a role is granted. It carries `@PreAuthorize("hasRole('ADMIN')")` — the first actual use of the long-standing `@EnableMethodSecurity` — so auth-service enforces it independently of the gateway, from the persisted row rather than the token claim.

**The first admin cannot be made through the API**, since nobody holds the role yet:

```sql
UPDATE auth_db.users SET role = 'ADMIN' WHERE username = 'someone';
```

**A role change is not visible at the gateway until the user logs in again.** The gateway has only the token claim to go on; auth-service reads the row. So a *promotion* takes effect at auth-service immediately but at the gateway only on a new token, and a **demotion leaves gateway-level ADMIN valid for up to the access-token lifetime (15 minutes)**. That is the cost of not giving the gateway a database, and it is why the access token is short-lived. `scripts/authz-check.sh` asserts this behaviour rather than papering over it.

Still missing, and both need the same thing: **there is no ownership check anywhere.** Any authenticated user can cancel any booking or edit any user by id. Fixing it needs a mapping between the token's `username` (auth_db) and `Booking.userId` (user_db) — two separate databases with no link between them. That mapping is the prerequisite, not the check.

`POST /api/auth/logout` validates the refresh token and returns a message — **it revokes nothing.** There is no token store or blacklist, so both tokens stay usable until they expire (access 15 min, refresh 7 days). Real logout means adding revocation state; do not treat the endpoint as if it already provides it.

### Version alignment

All twelve modules are on Spring Boot 4.1.0 / Spring Cloud 2025.1.2. They previously drifted (admin 4.0.0, payment 4.0.5, user's cloud train 2025.1.3) — keep them aligned when adding a module.

Each module must import the `spring-cloud-dependencies` BOM itself; the Boot parent does not manage Spring Cloud versions, and without the BOM the Eureka client dependency has no version and Maven cannot read the project at all.

`spring-boot-starter-web` and `spring-boot-starter-webmvc` both resolve on Boot 4 and both are in use — that inconsistency is cosmetic, not a bug. `spring-boot-starter-flyway` versus bare `flyway-core` is **not** cosmetic (see Schema ownership).
