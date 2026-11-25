# ISET Community — Complete Project Guide

A full explanation of the backend: what it does, how it is built, how each class and method works, and how security works. Everything here was read from the source code. Where something is a weakness or an oddity, it is flagged with **⚠**.

---

## Table of contents

1. [What the project is](#1-what-the-project-is)
2. [Technology stack](#2-technology-stack)
3. [How to run it](#3-how-to-run-it)
4. [Configuration (`application.properties`)](#4-configuration)
5. [Architecture](#5-architecture)
6. [Folder / package structure](#6-folder--package-structure)
7. [Data model (entities, inheritance, relations)](#7-data-model)
8. [Security in depth](#8-security-in-depth)
9. [REST API reference](#9-rest-api-reference)
10. [Business rules and permission matrix](#10-business-rules-and-permission-matrix)
11. [Class-by-class and method-by-method reference](#11-class-by-class-and-method-by-method-reference)
12. [Key flows step by step](#12-key-flows-step-by-step)
13. [File upload system](#13-file-upload-system)
14. [Error handling](#14-error-handling)
15. [The frontend (static test console)](#15-the-frontend)
16. [Tests](#16-tests)
17. [Known weaknesses and quirks](#17-known-weaknesses-and-quirks)
18. [Glossary of French names](#18-glossary-of-french-names)

---

## 1. What the project is

A **community platform for ISET students and professors** (ISET = Institut Supérieur des Études Technologiques, Tunisia). It is a Spring Boot REST API plus a small single-page test console served from the same server.

What users can do:

| Feature | Description |
|---|---|
| **Register / log in** | Only emails ending in `@iset.rnu.tn`. A new account is `PENDING` until an admin approves it. |
| **Admin approval** | The admin lists pending accounts and approves or rejects them. The admin can also delete accounts. |
| **Publications (posts)** | Any approved user can post a title + text, with an optional file attachment. Posts are either "general" (no community) or inside a community. |
| **Comments** | Users comment on posts they are allowed to see. |
| **Communities** | Professors and the admin create communities. A community is **public** (free to join) or **private** (needs an access key and has a member limit). |
| **Files** | Attachments are stored on disk with a random name, and can be downloaded by anyone allowed to view the post. |

Three roles exist: **STUDENT**, **PROF**, **ADMIN**.

---

## 2. Technology stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot **4.1.1** (parent POM) |
| Web | `spring-boot-starter-webmvc` (Spring MVC, REST controllers) |
| Persistence | Spring Data JPA + Hibernate |
| Database | MySQL (runtime) / H2 in-memory (tests) |
| Security | Spring Security 7 + **JWT** (`jjwt` 0.12.6, HMAC-signed) |
| Password hashing | BCrypt |
| API docs | springdoc-openapi (Swagger UI) 3.0.2 |
| Boilerplate removal | Lombok |
| Frontend | Plain HTML + CSS + vanilla JavaScript (no framework, no build step) |
| Build | Maven (wrapper included: `mvnw`, `mvnw.cmd`) |

---

## 3. How to run it

Prerequisites: JDK 17, a running MySQL server on `localhost:3306`.

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

- The database `iset-community` is **created automatically** (`createDatabaseIfNotExist=true`).
- Tables are created/updated automatically (`ddl-auto=update`).
- Server listens on **http://localhost:8081**.
- Test console: http://localhost:8081/
- Swagger UI: http://localhost:8081/swagger-ui.html
- On first start an admin account is created (default `admin@iset.tn` / `123`, see §4).

Environment variables you can set instead of editing the file: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.

Run tests: `./mvnw test` (uses H2, no MySQL needed).

---

## 4. Configuration

File: `src/main/resources/application.properties`.

| Property | Value | Meaning |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/iset-community?createDatabaseIfNotExist=true&...` | MySQL connection, DB auto-created |
| `spring.datasource.username` / `password` | `${DB_USERNAME:root}` / `${DB_PASSWORD:}` | Env var with a default (`root`, empty password) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Hibernate alters the schema to match entities at startup |
| `jwt.secret` | `${JWT_SECRET:<base64 default>}` | Base64 HMAC key. **⚠ A real default key is committed in the file.** |
| `jwt.expiration` | `86400000` | Token lifetime in ms = **24 hours** |
| `spring.servlet.multipart.max-file-size` | `20MB` | Max single upload |
| `spring.servlet.multipart.max-request-size` | `25MB` | Max whole multipart request |
| `app.upload-dir` | `uploads` | Folder where attachments are saved (relative to the working directory) |
| `app.email-domain` | `@iset.rnu.tn` | Required suffix of registration emails |
| `server.port` | `8081` | HTTP port |
| `app.admin.email` / `app.admin.password` | `${ADMIN_EMAIL:admin@iset.tn}` / `${ADMIN_PASSWORD:123}` | Bootstrap admin credentials. **⚠ Default password is `123`.** |
| `app.cors.allowed-origin-patterns` | `http://localhost:*` | Origins allowed by CORS |

Test profile (`src/test/resources/application.properties`): H2 in memory in MySQL mode, `create-drop`, a separate test JWT secret, uploads to `target/test-uploads`.

> Note: because `app.admin.email`/`password` are present in the properties file, the `@Value` defaults written in `AdminInitializer` (`admin@iset.rnu.tn`, `ChangeMe123!`) are never used in practice.

---

## 5. Architecture

A classic **layered architecture**:

```
 Browser / Swagger / Postman
            │  HTTP + JSON  (Authorization: Bearer <jwt>)
            ▼
 ┌──────────────────────────────────────────────────────┐
 │  Spring Security filter chain                          │
 │   CORS → JwtFilter → authorization rules               │
 └──────────────────────────────────────────────────────┘
            ▼
 ┌─────────────────┐   REST endpoints, HTTP status, DTO in/out
 │  controller/     │   (no business logic)
 └─────────────────┘
            ▼
 ┌─────────────────┐   business rules, permission checks,
 │  service/        │   transactions, file handling
 └─────────────────┘
            ▼
 ┌─────────────────┐   Spring Data JPA interfaces (CRUD + derived queries)
 │  repository/     │
 └─────────────────┘
            ▼
 ┌─────────────────┐   JPA entities mapped to MySQL tables
 │  model/          │
 └─────────────────┘
            ▼
          MySQL
```

Supporting packages:

- **`dto/`** — objects used on the HTTP boundary, so entities are never serialized directly (avoids infinite loops between bidirectional relations and avoids leaking the password hash).
- **`config/`** — security, JWT filter, password encoder, Swagger, admin bootstrap.

**Design principles you can see in the code**

1. **Controllers are thin.** They map HTTP ↔ service calls and return a DTO.
2. **Services own the rules.** Every permission check and validation lives in a service.
3. **Identity comes from the token, never from the request.** Controllers receive the logged-in user through `@AuthenticationPrincipal User current` and pass `current.getId()` down; no endpoint accepts a "userId" parameter from the client. This prevents impersonation.
4. **Stateless.** No HTTP session; every request carries its own JWT.
5. **Errors are exceptions.** Services throw `ResponseStatusException(status, message)`; one `@RestControllerAdvice` turns them into `{ "message": "..." }` JSON.
6. **Constructor injection** everywhere (no `@Autowired` fields), except `JwtService` which uses `@Value` on fields.

---

## 6. Folder / package structure

```
backend/
├── pom.xml                      Maven build + dependencies
├── mvnw / mvnw.cmd / .mvn/      Maven wrapper
├── uploads/                     Runtime folder for attached files (git-ignored)
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── DemoApplication.java        Spring Boot entry point (main)
    │   │   ├── config/
    │   │   │   ├── AdminInitializer.java   Creates the first admin at startup
    │   │   │   ├── JwtFilter.java          Reads the JWT on each request
    │   │   │   ├── PasswordConfig.java     BCrypt PasswordEncoder bean
    │   │   │   ├── SecurityConfig.java     Filter chain, route rules, CORS
    │   │   │   └── SwaggerConfig.java      Adds "Bearer" auth to Swagger UI
    │   │   ├── controller/
    │   │   │   ├── AuthController.java         /api/auth/*
    │   │   │   ├── AdminController.java        /api/admin/*
    │   │   │   ├── UserController.java         /api/users/*
    │   │   │   ├── PublicationController.java  /api/publications/*
    │   │   │   ├── CommentaireController.java  comments endpoints
    │   │   │   ├── CommunityController.java    /api/communities/*
    │   │   │   └── GlobalExceptionHandler.java error → JSON
    │   │   ├── service/
    │   │   │   ├── AuthService.java        login
    │   │   │   ├── UserService.java        register, list, approve/reject, delete
    │   │   │   ├── JwtService.java         create / parse tokens
    │   │   │   ├── AccessService.java      role + membership rules
    │   │   │   ├── PublicationService.java posts + download
    │   │   │   ├── CommentaireService.java comments
    │   │   │   ├── CommunityService.java   communities
    │   │   │   └── FileStorageService.java disk storage
    │   │   ├── repository/                 5 Spring Data interfaces
    │   │   ├── model/                      JPA entities + UserStatus enum
    │   │   └── dto/                        request / response objects
    │   └── resources/
    │       ├── application.properties
    │       └── static/ (index.html, app.js, styles.css)   test console
    └── test/
        ├── java/.../DemoApplicationTests.java
        └── resources/application.properties            H2 test config
```

---

## 7. Data model

### 7.1 Entity overview

| Entity | Table | Purpose |
|---|---|---|
| `User` (abstract-ish base) | `users` | Common account data |
| `Student` | `student` | Student account |
| `Prof` | `prof` | Professor account |
| `Admin` (extends `Prof`) | `admin` | Administrator account |
| `Community` | `community` | Base community |
| `PublicCommunity` | `public_community` | Open community |
| `PrivateCommunity` | `private_community` | Key-protected, limited community |
| `Publication` | `publication` | A post |
| `Commentaire` | `commentaire` | A comment on a post |
| *(join table)* | `community_members` | Many-to-many: community ↔ user |

### 7.2 Inheritance (JPA `JOINED` strategy)

```
User  (users table: id, fullName, email, password, departement, status, user_type)
 ├── Student
 └── Prof
      └── Admin          ← an Admin IS a Prof
```

```
Community (community table: id, name, isPrivate, user_id)
 ├── PublicCommunity
 └── PrivateCommunity    (adds access_key, nbMax)
```

`JOINED` means: the parent table holds the shared columns, each subclass has its own table with only its extra columns, linked by primary key. Querying `User` joins the child tables, so the real Java class (`Student`, `Prof`, `Admin`) is rebuilt automatically — that is how the app knows a user's role without a "role" column.

`@DiscriminatorValue` (`"STUDENT"`, `"PROF"`, `"ADMIN"`, `"PUBLIC"`, `"PRIVATE"`) is also set on each subclass.

**Role detection = `instanceof`.** Because `Admin extends Prof`:
- `user instanceof Admin` → admin only
- `user instanceof Prof` → professors **and** admins

### 7.3 Fields

**User**
| Field | Notes |
|---|---|
| `id` | `Integer`, auto-increment |
| `fullName` | |
| `email` | `@Column(unique = true)`; stored lower-cased |
| `password` | BCrypt hash |
| `departement` | free text |
| `status` | `UserStatus` enum stored as string: `PENDING` (default), `APPROVED`, `REJECTED` |
| `publications`, `commentaires`, `communities` | one-to-many, `mappedBy = "user"`, **`cascade = ALL`** |

**Community**: `id`, `name`, `isPrivate` (Boolean), `user` (creator, many-to-one), `members` (`Set<User>`, many-to-many via `community_members`), `publications` (one-to-many, `cascade = REMOVE`).

**PrivateCommunity**: `accessKey` (column `access_key`, **BCrypt-hashed**), `nbMax`.

**Publication**: `id`, `title`, `content` (length 5000), `createdAt`, `originalFileName` (what the user uploaded), `storedFileName` (random name on disk), `user` (author), `community` (nullable → general feed), `commentaires` (`cascade = ALL, orphanRemoval = true`).

**Commentaire**: `id`, `contenu` (length 2000), `date`, `user` (author), `publication`.

### 7.4 Relationship diagram

```
User 1───* Publication *───1 Community (optional)
 │ 1                │ 1
 │                  │
 *                  *
Commentaire *───1 Publication
 │
 └───1 User (author)

User 1───* Community        (creator)
User *───* Community        (membership, table community_members)
```

### 7.5 Cascade behaviour (important when deleting)

| Deleting… | What disappears automatically |
|---|---|
| A **User** | their publications (and those posts' comments), their comments, **communities they created** (and those communities' posts) |
| A **Community** | all its publications (cascade REMOVE) → their comments |
| A **Publication** | its comments (cascade ALL + orphanRemoval) |
| A **Comment** | only itself (removed from its publication's list → orphanRemoval deletes the row) |

Files on disk are **not** removed by JPA; the services delete them explicitly (see §13).

### 7.6 `equals` / `hashCode`

`User` and `Community` define `equals` on **`id` only** and a **constant `hashCode`** (the class hash). Reason (stated in a code comment): the Lombok `@Data` default included the collections, which are circular (`User ↔ Publication ↔ Community`), causing a `StackOverflowError` as soon as a `User` was put in a `HashSet` (`community.getMembers().add(user)`). A constant hashCode is legal and safe with Hibernate (ids are null before save) at the cost of all users landing in the same hash bucket.

---

## 8. Security in depth

### 8.1 Authentication model: stateless JWT

1. The client sends email + password to `POST /api/auth/login`.
2. If valid and the account is `APPROVED`, the server returns a **JWT**.
3. For every later request the client sends `Authorization: Bearer <token>`.
4. No session cookie is ever created (`SessionCreationPolicy.STATELESS`).

### 8.2 The token (`JwtService`)

- **Algorithm:** HMAC-SHA (key length decides HS256/384/512; the 48-byte default key → HS384). Key = Base64-decoded `jwt.secret`.
- **Claims:** `sub` = the user's **email**, `iat` = issue time, `exp` = issue time + 24h. **No role claim** — roles are read from the database on every request.
- **Signing:** `Jwts.builder()...signWith(key)`.
- **Parsing:** `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` — verifies signature and expiry; throws `JwtException` if invalid or expired.

### 8.3 Per-request flow (`JwtFilter`)

```
Request ──► CORS ──► JwtFilter ──► authorization rules ──► controller
```

`JwtFilter extends OncePerRequestFilter`. For each request:

1. Read the `Authorization` header; continue only if it starts with `Bearer `.
2. Parse the token → get the email.
3. Load the user from the DB by email, and **require `status == APPROVED`**.
4. If found, build a `UsernamePasswordAuthenticationToken(user, null, authorities)` and put it in the `SecurityContext`. The **principal is the `User` entity itself**, which is why controllers can write `@AuthenticationPrincipal User current`.
5. If the token is invalid/expired (`JwtException` / `IllegalArgumentException`) → clear the context; the request stays **anonymous**.
6. Always call `filterChain.doFilter(...)`.

Consequences of this design:

- **Instant revocation:** if an admin rejects or deletes someone, their existing token stops working immediately (DB check on every request), even though it has not expired.
- **Live roles:** roles are never baked into the token, so promoting/demoting takes effect at once.
- **Cost:** one DB query per authenticated request.

> The filter is intentionally **not** annotated `@Component` (comment in the file). If it were, Spring Boot would also register it as a plain servlet filter, running it twice and outside the Security chain. It is created manually inside `SecurityConfig` instead.

### 8.4 Authorities (roles)

Assigned in `JwtFilter.authoritiesOf(user)`:

| Real type | Authorities |
|---|---|
| `Admin` | `ROLE_ADMIN`, `ROLE_PROF` |
| `Prof` | `ROLE_PROF` |
| anything else | `ROLE_STUDENT` |

### 8.5 Route rules (`SecurityConfig`)

Evaluated top to bottom, first match wins:

| Rule | Who |
|---|---|
| `OPTIONS /**` | public (CORS preflight) |
| `GET /`, `/index.html`, `/styles.css`, `/app.js` | public (the test console) |
| `/api/auth/register`, `/api/auth/login` | public |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | public |
| `/api/admin/**` | `hasRole("ADMIN")` |
| everything else (`anyRequest`) | any authenticated user |

Other settings:

- **CSRF disabled** — acceptable here because auth is a header token, not a cookie, so browsers can't be tricked into sending credentials automatically.
- **Unauthenticated → 401** via `HttpStatusEntryPoint(UNAUTHORIZED)` (Spring's default would be 403).
- **Authenticated but wrong role → 403** (default access-denied handler).
- `/api/auth/me` is **not** public: only register and login are.

### 8.6 CORS

Allowed origin **patterns**: `http://localhost:*` (configurable). Methods `GET, POST, PUT, DELETE, OPTIONS`; all headers; credentials allowed; `Content-Disposition` exposed so the browser can read the download filename.

### 8.7 Passwords

- Hashed with **BCrypt** (`BCryptPasswordEncoder`, default strength 10, salted automatically).
- Minimum 8 characters at registration.
- Login verifies with `passwordEncoder.matches(raw, hash)`.
- The hash is never returned: `UserResponseDTO` has no password field.

### 8.8 Account lifecycle / gatekeeping

```
register ──► PENDING ──admin approve──► APPROVED ──► can log in
                 └─────admin reject───► REJECTED ──► login refused (403)
```

Login returns **403** with a different message for `REJECTED` ("Votre inscription a été refusée") vs. `PENDING` ("pas encore approuvé"). These messages are only reached **after** a correct password, so they don't leak whether an email exists. Wrong email and wrong password both return the same **401** "Email ou mot de passe incorrect" (no user enumeration through login).

### 8.9 Registration hardening

- Domain restricted to `@iset.rnu.tn`.
- Email normalised (`trim().toLowerCase()`), unique.
- `userType` accepts only `STUDENT` or `PROF`. **`ADMIN` is rejected** — nobody can self-register as admin. The only admin is created by `AdminInitializer`.
- The request DTO has no `status`/`id` field, and the service forces `PENDING`, so a client cannot choose its own status (no mass-assignment).

### 8.10 Authorization checks inside services ("object-level" security)

Route rules say *who may call an endpoint*; services decide *which objects* they may touch:

| Check | Where | Rule |
|---|---|---|
| `canView(user, publication)` | `AccessService` | public/general post → everyone; post in a **private** community → members + admin only |
| `checkOwnerOrAdmin` | `PublicationService` | edit/delete a post: author or admin |
| comment delete | `CommentaireService` | author or admin |
| community delete | `CommunityService` | creator or admin |
| post in a community | `PublicationService.create` | must be a member (admin exempt) |
| create community | `CommunityService.create` | `Prof` or `Admin` only |
| download file | `PublicationService.download` | same as `canView` |

### 8.11 File security

See §13: extension whitelist, random UUID names, path-traversal guard, size cap, forced `attachment` download with `application/octet-stream`.

### 8.12 Private community key

The key is **hashed with BCrypt** before storage (`passwordEncoder.encode`) and verified with `matches` on join — it is never stored or returned in clear text. `CommunityResponseDTO` does not expose it.

### 8.13 Frontend XSS protection

All user-provided text rendered by `app.js` goes through `escapeHtml()` before being placed in `innerHTML`.

### 8.14 Security weaknesses (honest list)

See §17: committed default JWT secret and admin password `123`, token in `localStorage`, no rate limiting, no input-validation annotations, every user can list all users, etc.

---

## 9. REST API reference

Base URL `http://localhost:8081`. "Auth" = needs `Authorization: Bearer <token>`.

### 9.1 Authentication — `/api/auth`

| Method | Path | Auth | Body | Success | Errors |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | public | `{fullName, departement, email, password, userType}` | `201` + `UserResponseDTO` (status `PENDING`) | 400 (missing field, password < 8, wrong domain, bad type), 409 (email taken) |
| POST | `/api/auth/login` | public | `{email, password}` | `200` `{token, message}` | 400 (missing), 401 (wrong credentials), 403 (rejected / pending) |
| GET | `/api/auth/me` | any user | – | `200` `UserResponseDTO` of the caller | 401 |

### 9.2 Users — `/api/users`

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/users` | any user | list of all users (`UserResponseDTO`) |
| GET | `/api/users/{id}` | any user | one user, or 404 |

### 9.3 Administration — `/api/admin` (ADMIN only → otherwise 403)

| Method | Path | Action |
|---|---|---|
| GET | `/api/admin/users` | all accounts |
| GET | `/api/admin/pending-users` | accounts with status `PENDING` |
| PUT | `/api/admin/users/{id}/approve` | set `APPROVED` |
| PUT | `/api/admin/users/{id}/reject` | set `REJECTED` |
| DELETE | `/api/admin/users/{id}` | delete the account + cascade; `204` |

Admin accounts cannot be approved/rejected/deleted (400).

### 9.4 Publications — `/api/publications`

| Method | Path | Notes |
|---|---|---|
| GET | `/api/publications?communityId=` | feed, newest first, filtered by what the caller may see; optional community filter |
| POST | `/api/publications` | **`multipart/form-data`**: `title` (required), `content`, `communityId`, `file`. `201` |
| PUT | `/api/publications/{id}` | JSON `{title, content}`; author or admin. File and community cannot be changed |
| DELETE | `/api/publications/{id}` | author or admin; also deletes the file; `204` |
| GET | `/api/publications/{id}/file` | downloads the attachment (`Content-Disposition: attachment`) |

### 9.5 Comments

| Method | Path | Notes |
|---|---|---|
| POST | `/api/publications/{id}/comments` | JSON `{contenu}`; caller must be able to view the post; `201` + `CommentDTO` |
| DELETE | `/api/comments/{id}` | author or admin; `204` |

### 9.6 Communities — `/api/communities`

| Method | Path | Notes |
|---|---|---|
| GET | `/api/communities` | all communities, each with `member` = is the caller a member |
| POST | `/api/communities` | JSON `{name, type: PUBLIC|PRIVATE, key?, nbMax?}`; Prof/Admin only; `201` |
| POST | `/api/communities/{id}/join` | optional body `{key}` (required for PRIVATE) |
| POST | `/api/communities/{id}/leave` | `204`; creator cannot leave |
| DELETE | `/api/communities/{id}` | creator or admin; `204` |

### 9.7 DTO shapes

```jsonc
// UserResponseDTO
{ "id": 1, "fullName": "…", "departement": "…", "email": "…",
  "status": "APPROVED", "userType": "STUDENT|PROF|ADMIN" }

// AuthResponseDTO
{ "token": "eyJ…", "message": "Connexion réussie" }

// PublicationResponseDTO
{ "id": 5, "title": "…", "content": "…", "createdAt": "2026-11-24T10:15:30",
  "authorId": 2, "authorName": "…", "authorType": "PROF",
  "communityId": 3, "communityName": "…", "fileName": "cours.pdf",
  "comments": [ { "id": 9, "contenu": "…", "date": "…", "authorId": 4, "authorName": "…" } ] }

// CommunityResponseDTO
{ "id": 3, "name": "…", "type": "PUBLIC|PRIVATE", "ownerName": "…", "ownerId": 2,
  "membersCount": 12, "nbMax": 30 /* null if public */, "member": true }
```

---

## 10. Business rules and permission matrix

| Action | Student | Prof | Admin |
|---|:--:|:--:|:--:|
| Register (needs approval) | ✅ | ✅ | ❌ (created at startup) |
| Log in | ✅ if APPROVED | ✅ if APPROVED | ✅ |
| See all users | ✅ | ✅ | ✅ |
| Approve / reject / delete accounts | ❌ | ❌ | ✅ (not other admins) |
| Create a post (general feed) | ✅ | ✅ | ✅ |
| Post inside a community | member only | member only | any community |
| Edit / delete own post | ✅ | ✅ | ✅ |
| Edit / delete anyone's post | ❌ | ❌ | ✅ |
| Comment on a visible post | ✅ | ✅ | ✅ |
| Delete own comment | ✅ | ✅ | ✅ |
| Delete anyone's comment | ❌ | ❌ | ✅ |
| Create a community | ❌ | ✅ | ✅ |
| Join a public community | ✅ | ✅ | ✅ |
| Join a private community | with key & room | with key & room | with key & room |
| Leave a community | ✅ (not creator) | ✅ (not creator) | ✅ (not creator) |
| Delete a community | ❌ | own only | any |
| See posts of a private community | members only | members only | ✅ |

Validation limits: title ≤ 200 chars, post content ≤ 5000, comment ≤ 2000 and not blank, full name required, password ≥ 8, private key ≥ 4 chars, `nbMax` ≥ 1.

---

## 11. Class-by-class and method-by-method reference

### 11.1 `DemoApplication`
`main(String[] args)` — starts Spring Boot (`@SpringBootApplication` = component scan + auto-configuration + configuration).

---

### 11.2 Configuration package

#### `AdminInitializer` (`CommandLineRunner`)
Runs once after the context starts. Exists because registration refuses `ADMIN` and only an admin can approve accounts — without an initial admin nobody could ever log in.

- **constructor** — injects repositories, encoder, and the email/password from properties; email is trimmed + lower-cased.
- **`run(String... args)`**
  1. If `adminRepository.count() > 0` → do nothing (idempotent).
  2. If the configured email is already used by another account → log a warning and stop.
  3. Otherwise create an `Admin` (`fullName="Administrateur"`, `departement="Administration"`, BCrypt password, status `APPROVED`) and save. Logs a warning reminding you to change the default password.

#### `JwtFilter` (`OncePerRequestFilter`)
- **`doFilterInternal(request, response, chain)`** — described in §8.3.
- **`authoritiesOf(User)`** — maps entity type to `ROLE_*` authorities (§8.4).

#### `PasswordConfig`
- **`passwordEncoder()`** — `@Bean` returning `BCryptPasswordEncoder`. Used by `UserService`, `AuthService`, `CommunityService`, `AdminInitializer`.

#### `SecurityConfig`
- **`filterChain(HttpSecurity)`** — builds the chain (§8.5): CSRF off, CORS on, stateless, 401 entry point, route rules, and `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`.
- **`corsConfigurationSource(originPatterns)`** — builds the CORS policy (§8.6).

#### `SwaggerConfig`
- **`customOpenAPI()`** — declares a global `BearerAuth` (HTTP bearer, JWT) scheme so Swagger UI shows an **Authorize** button.

---

### 11.3 Model package
Plain JPA entities with Lombok `@Getter/@Setter` (and `@Data` on `PrivateCommunity`). Only `User` and `Community` contain code: the custom `equals`/`hashCode` (§7.6). `UserStatus` is the enum `PENDING, APPROVED, REJECTED`.

---

### 11.4 Repository package
All extend `JpaRepository<Entity, Integer>` (free `save`, `findById`, `findAll`, `delete`, `count`…).

| Repository | Extra methods |
|---|---|
| `UserRepository` | `findByEmail(String)`, `existsByEmail(String)`, `findByStatus(UserStatus)` |
| `CommunityRepository` | `findByMembersContaining(User)` — communities where the user is a member |
| `PublicationRepository` | `findAllByOrderByCreatedAtDesc()` — newest first |
| `AdminRepository`, `CommentaireRepository` | none (used for `count()`, `save`, `findById`) |

Spring Data builds the queries from the method names (no SQL written).

---

### 11.5 DTO package

| DTO | Kind | Role |
|---|---|---|
| `RegisterRequestDTO` | class (Lombok get/set) | input of register |
| `LoginRequestDTO` | class | input of login |
| `AuthResponseDTO` | class | `{token, message}` |
| `UserResponseDTO` | class | output; **`fromEntity(User)`** unproxies the entity (`Hibernate.unproxy`) because a lazy proxy is not `instanceof Student/Prof/Admin`, then computes `userType` |
| `PublicationRequestDTO` (record) | input of edit: `title`, `content` |
| `PublicationResponseDTO` (record) | output; **`fromEntity(Publication)`** builds author info, community info, file name, and maps comments |
| `CommentRequestDTO` (record) | `contenu` |
| `CommentDTO` (record) | output; **`fromEntity(Commentaire)`** |
| `CommunityRequestDTO` (record) | `name, type, key, nbMax` |
| `CommunityResponseDTO` (record) | output; **`fromEntity(Community, currentUserId)`** computes `type`, `membersCount`, `nbMax`, and whether the caller is a member |
| `JoinRequestDTO` (record) | `key` |

Records (Java 16+) are immutable data carriers: `request.contenu()` instead of `getContenu()`.

---

### 11.6 Service package

#### `JwtService`
| Method | What it does |
|---|---|
| `getSigningKey()` (private) | Base64-decodes `jwt.secret`, builds an HMAC `SecretKey` |
| `generateToken(email)` | token with subject=email, issuedAt=now, expiration=now+`jwt.expiration` |
| `extractEmail(token)` | verifies + returns the subject |
| `extractAllClaims(token)` (private) | signature + expiry verification |

#### `AuthService`
- **`login(LoginRequestDTO)`**: ① both fields present, else 400 → ② email trimmed/lower-cased, find user else 401 → ③ BCrypt match else 401 (same message) → ④ status `REJECTED` → 403, anything not `APPROVED` → 403 → ⑤ `generateToken(email)` → ⑥ return `{token, "Connexion réussie"}`.

#### `UserService`
| Method | What it does |
|---|---|
| `registerUser(User, userType)` | Validates name, email, password ≥ 8 → lowercases email and checks the ISET domain → checks email uniqueness (409) → requires `userType` → creates `Student` or `Prof` (anything else, including `ADMIN`, → 400) → BCrypt-hashes password → status `PENDING` → saves |
| `getAllUsers()` | `findAll()` |
| `getUserById(id)` | or 404 "Utilisateur introuvable" |
| `getPendingUsers()` | `findByStatus(PENDING)` |
| `updateUserStatus(id, status)` | refuses on an Admin (400); sets and saves |
| `deleteUser(id)` (`@Transactional`) | refuses on an Admin; ① deletes disk files of the user's posts ② deletes disk files of posts in communities the user **created** ③ removes the user from communities where they are only a member ④ `userRepository.delete(user)` → cascade removes posts, comments, created communities |
| `badRequest(msg)` (private) | builds a 400 exception |

#### `AccessService` (`@Component`) — shared rules
| Method | Rule |
|---|---|
| `isAdmin(user)` | `Hibernate.unproxy(user) instanceof Admin` |
| `isProfOrAdmin(user)` | `instanceof Prof` (Admin is a Prof) |
| `isMember(community, user)` | user id is in `community.members` |
| `canView(user, publication)` | no community → true; public community → true; private community → admin or member |

#### `PublicationService`
| Method | What it does |
|---|---|
| `list(userId, communityId)` | all posts newest first → keep those `canView` → optionally keep those of `communityId` → map to DTO. **Filtering happens in memory** after loading everything |
| `create(userId, title, content, communityId, file)` | validates title (required, ≤ 200) and content (≤ 5000) → sets author and timestamp → if `communityId`: community must exist (404) and user must be member or admin (403) → if a non-empty file: stores it and records original + stored names → saves |
| `update(id, userId, dto)` | owner-or-admin; title required; updates title/content only |
| `delete(id, userId)` | owner-or-admin; deletes the disk file then the row (comments cascade) |
| `download(id, userId)` | `canView` else 403; no file → 404; returns `FileDownload(resource, originalName)` |
| `FileDownload` | nested record `(Resource resource, String fileName)` |
| `checkOwnerOrAdmin`, `getUser`, `getPublication` (private) | helpers |

#### `CommentaireService`
| Method | What it does |
|---|---|
| `add(publicationId, userId, contenu)` | post must exist (404), caller must `canView` (403), content not blank (400) and ≤ 2000 (400); trims, timestamps, saves; returns `CommentDTO` |
| `delete(commentId, userId)` | author or admin (403 otherwise); removes it from `publication.getCommentaires()` so `orphanRemoval` deletes the row |

#### `CommunityService`
| Method | What it does |
|---|---|
| `list(userId)` | all communities → DTO with `member` flag (read-only transaction) |
| `create(userId, dto)` | caller must be Prof/Admin (403); name required; type required; `PUBLIC` → `PublicCommunity`; `PRIVATE` → key ≥ 4 chars and `nbMax` ≥ 1, key BCrypt-hashed → sets name, creator, and **adds creator to members** |
| `join(communityId, userId, key)` | already member → 409; if private: wrong/missing key → 403, then full (`members.size() >= nbMax`) → 409 (the key is checked first, so a non-member learns nothing about capacity); adds member |
| `leave(communityId, userId)` | creator cannot leave (400); else removes the user from members |
| `delete(communityId, userId)` | creator or admin; deletes disk files of all its posts, then the community (posts cascade) |

#### `FileStorageService` — see §13.

---

### 11.7 Controller package

| Controller | Method → endpoint |
|---|---|
| `AuthController` | `register` → POST `/register` (builds a `User` from the DTO then calls `UserService.registerUser`); `login` → POST `/login`; `me` → GET `/me` |
| `AdminController` | `getAllUsers`, `getPendingUsers`, `approveUser`, `rejectUser`, `deleteUser` |
| `UserController` | `getAllUsers`, `getUserById` |
| `PublicationController` | `list`, `create` (multipart), `update`, `delete`, `download` (sets `Content-Disposition` with UTF-8 filename, type `application/octet-stream`) |
| `CommentaireController` | `add` (POST `/api/publications/{id}/comments`), `delete` (DELETE `/api/comments/{id}`) — mapped at `/api` |
| `CommunityController` | `list`, `create`, `join` (body optional), `leave`, `delete` |
| `GlobalExceptionHandler` | see §14 |

---

## 12. Key flows step by step

### 12.1 Registration → approval → login

```
1. POST /api/auth/register {…, userType:"STUDENT"}
     AuthController → UserService.registerUser
       validate → domain check → unique email → new Student → BCrypt → PENDING → save
     ← 201 {status:"PENDING"}

2. (Admin) GET /api/admin/pending-users → PUT /api/admin/users/{id}/approve
     UserService.updateUserStatus → APPROVED

3. POST /api/auth/login {email,password}
     AuthService.login → checks → JwtService.generateToken
     ← 200 {token}

4. GET /api/auth/me   (Authorization: Bearer token)
     JwtFilter: token → email → DB user (APPROVED) → SecurityContext
     AuthController.me(@AuthenticationPrincipal User)
     ← 200 UserResponseDTO
```

### 12.2 Creating a post with a file in a private community

```
POST /api/publications  (multipart: title, content, communityId=7, file=cours.pdf)
 JwtFilter authenticates
 PublicationController.create
  PublicationService.create
    validate title/content
    community 7 exists? → user is member or admin?        (else 404 / 403)
    FileStorageService.store(file)  → extension whitelist → "<uuid>.pdf" on disk
    save Publication(originalFileName="cours.pdf", storedFileName="<uuid>.pdf")
 ← 201 PublicationResponseDTO
```

### 12.3 Joining a private community

```
POST /api/communities/7/join   {"key":"abcd"}
  CommunityService.join
    already member?        → 409
    is PrivateCommunity?
       BCrypt.matches(key) → else 403
       full?               → 409
    members.add(user) → save
 ← 200 CommunityResponseDTO (member=true)
```

### 12.4 Admin deletes a user

```
DELETE /api/admin/users/12     (route rule: ROLE_ADMIN)
  UserService.deleteUser (@Transactional)
    refuse if target is an Admin
    delete files: user's posts + posts in communities they own
    remove user from other communities' member lists
    userRepository.delete(user)
       → cascade: their posts+comments, their comments elsewhere, communities they created (+ those posts)
 ← 204
```

---

## 13. File upload system

`FileStorageService`, root folder = `app.upload-dir` (`uploads`) resolved to an absolute normalised path; created at startup.

**`store(MultipartFile)`**
1. `StringUtils.cleanPath(originalFilename)` (strips `..` segments).
2. Extract the extension (lower-case). **Whitelist:** `pdf, doc, docx, ppt, pptx, xls, xlsx, txt, zip, png, jpg, jpeg, gif, mp4, mp3`; otherwise 400.
3. Generate `UUID.randomUUID() + "." + extension` — the user's filename is **never used on disk**.
4. Copy the stream to `root/<storedName>`; on I/O error → 500.
5. Return `storedName` (kept in `Publication.storedFileName`; the user-visible name is kept separately in `originalFileName`).

**`load(storedName)`** — resolves the path, `normalize()`s it, and requires `file.startsWith(root)` (**path-traversal guard**) and that it exists; else 404. Returns a `PathResource`.

**`delete(storedName)`** — null-safe, same traversal guard, `Files.deleteIfExists`; failures are swallowed so a stuck file can't block a delete.

Size limits: 20 MB per file / 25 MB per request (Spring multipart), with a friendly 413 message in `GlobalExceptionHandler`.

Download response: `Content-Disposition: attachment; filename*=UTF-8''…` + `application/octet-stream`, so the browser saves rather than renders the file (prevents inline script execution from uploaded content).

**Limitation:** only the extension is checked, not the real file content.

---

## 14. Error handling

All errors become `{ "message": "<text>" }`.

| Source | HTTP status | Message |
|---|---|---|
| `ResponseStatusException` (thrown by services) | the one given | the reason text |
| `MaxUploadSizeExceededException` | 413 | "Fichier trop volumineux (20 Mo maximum)" |
| `HttpMessageNotReadableException` (bad/missing JSON) | 400 | "Corps de requête invalide" |
| `DataIntegrityViolationException` (DB constraint) | 409 | "Opération impossible : données en conflit" |
| No/invalid token on a protected route | 401 (empty body, from the security entry point) | – |
| Wrong role (e.g. non-admin on `/api/admin/**`) | 403 | – |

Status codes used by the services: 400 validation, 401 bad credentials/unknown user, 403 forbidden, 404 not found, 409 conflict (duplicate email, already member, community full).

---

## 15. The frontend

`src/main/resources/static/` — served by Spring Boot at `/`. A **test console**, not a production UI.

| File | Content |
|---|---|
| `index.html` | layout: sidebar, login/register panel, composer, feed, communities, users directory, admin panel, response drawer |
| `styles.css` | styling |
| `app.js` | all logic in one IIFE |

Key points of `app.js`:

- **State:** `token` (in `localStorage["iset.jwt"]`), `api` base URL (fixed: same origin, or `localhost:8081` when opened as a file), `me`, `communities`, `users`.
- **`request(path, options)`** — the single HTTP helper: adds `Authorization: Bearer`, sets JSON content type (not for `FormData`), parses JSON/text/204, writes every response to the "Réponse API" drawer, throws `Error(message)` on non-2xx.
- **Rendering:** `renderPosts`, `renderCommunities`, `renderUsers`, `renderPendingUsers`, `renderAdminUsers` build HTML strings; every dynamic value goes through `escapeHtml`.
- **Auth flow:** login → store token → `GET /api/auth/me` → `setSignedIn(user)` → `refreshData()` (posts + communities in parallel via `Promise.allSettled`) → if admin, `loadAdminData()`. `signOut()` clears everything.
- **Event delegation:** one listener on `#feed-list` handles comment submit, post edit, delete, download; one on `#community-list` handles join/leave/delete; admin lists handle approve/reject/delete.
- **File download:** done with `fetch` + Bearer header → `Blob` → temporary `<a download>` (a plain link couldn't send the header).
- **Role-based UI:** the Admin nav link is shown only if `userType === "ADMIN"`. This is **cosmetic**; the real enforcement is on the server.
- **On load (`initialize`):** pings `/v3/api-docs` to show API status; if a token exists, tries `/api/auth/me` and auto-logs in, otherwise `signOut()`.

---

## 16. Tests

`DemoApplicationTests.contextLoads()` — a single smoke test that starts the whole Spring context against H2 (`src/test/resources/application.properties`). There are **no unit or integration tests** of the business logic. Test dependencies for JPA, Security, Validation and WebMVC are already in the POM if you want to add some (e.g. `MockMvc` tests of the permission matrix in §10).

---

## 17. Known weaknesses and quirks

### Security
1. **⚠ Default secrets are committed** — `jwt.secret` default key and admin password `123` in `application.properties`. Anyone with the repo can forge tokens or log in as admin unless env vars are set.
2. **⚠ Any logged-in user can list all users** (`GET /api/users`, `/api/users/{id}`), including emails, departments and statuses. Only `/api/admin/**` is admin-restricted.
3. **⚠ Token kept in `localStorage`** in the console (readable by any XSS). No logout on the server side (stateless; a token remains valid until it expires, except it dies immediately if the account is no longer `APPROVED`).
4. **⚠ No rate limiting / lockout** on login → brute-force possible.
5. **⚠ No `@Valid` / Bean Validation** — validation is hand-written in services. `RegisterRequestDTO`/`LoginRequestDTO` have no constraints (the validation starter was removed since nothing used it).
6. **⚠ File type is checked by extension only**, not by content.
7. **⚠ All communities (including private names) are visible to everyone** via `GET /api/communities`; only their posts are hidden.
8. **⚠ Swagger and `/v3/api-docs` are public** — fine in development, usually closed in production.
9. `hasRole("ADMIN")` guard + service checks give defence in depth on admin actions, which is good.

### Logic / data
10. **`Publication.list` loads every post then filters in memory** and builds DTOs with each post's comments → won't scale; typical N+1 query risk.
11. `nbMax` only applies to private communities; public ones are unlimited.
12. Deleting a user also deletes **communities they created** (cascade) — all members lose them.
13. `Community.isPrivate` duplicates information already carried by the subclass type.

---

## 18. Glossary of French names

| Term | Meaning |
|---|---|
| `Commentaire` / `contenu` | comment / its text |
| `Publication` | post |
| `departement` | department |
| `Prof` | professor |
| `nbMax` | maximum number of members |
| `Communauté` | community |
| `Fil d'actualité` | news feed |
| `Inscription` | registration |
| `Clé d'accès` | access key (private communities) |
| `Introuvable` | not found |
| `Accès refusé` | access denied |
