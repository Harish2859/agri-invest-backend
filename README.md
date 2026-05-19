# AgriInvest Platform Backend

Spring Boot backend for a role-based agri-investment platform where investors fund farmer projects, village leads verify operational milestones/KYC, and settlements distribute final returns.

## Tech Stack
- Java 21, Spring Boot 3.2.4
- Spring Web, Spring Security (JWT), Spring Data JPA
- PostgreSQL
- Lombok, Maven

## Project Structure
```
src/main/java/com/agriinvest/platform
  ├─ controller/      # REST APIs
  ├─ service/         # Business logic
  ├─ entity/          # JPA domain models
  ├─ repository/      # Data access
  ├─ security/        # JWT utils/filter
  └─ config/          # Security configuration
src/main/resources
  ├─ application.properties
  └─ schema.sql
```

## Getting Started
1. Install Java 21 and PostgreSQL.
2. Create database `agri_invest`.
3. Configure `src/main/resources/application.properties`.
4. Run:
```bash
./mvnw spring-boot:run
```
Backend starts on `http://localhost:8081`.

## Configuration
Current defaults in `application.properties`:
- `spring.datasource.url=jdbc:postgresql://localhost:5432/agri_invest`
- `spring.datasource.username=postgres`
- `spring.datasource.password=1234`
- `spring.jpa.hibernate.ddl-auto=update`
- `spring.sql.init.mode=always`
- `server.port=8081`

Recommended for production:
- Move DB credentials and JWT secret to environment variables.
- Replace hardcoded JWT secret in `JwtUtils`.
- Disable `show-sql` and use managed migrations (Flyway/Liquibase).

## Authentication & Authorization
- Auth base path: `/api/auth`
- JWT-based stateless auth (`Authorization: Bearer <token>`)
- Token currently contains `sub=email`, validity 24h.
- Security rules (`ProjectSecurityConfig`):
  - Public: `/api/auth/**`, `OPTIONS /**`, `/api/projects/discover`, `/error`
  - Lead-only: `/api/admin/**`
  - Farmer-only: `POST /api/projects/*/settle`
  - All remaining routes require authentication
- Method-level role checks use `@PreAuthorize`.

## Roles
- `INVESTOR`
- `FARMER`
- `VILLAGE_LEAD`

## Core Business Flow
1. User signup/login.
2. Farmer submits KYC and creates project.
3. Lead verifies farmer and approves project.
4. Investors initiate and complete investments.
5. Project reaches `FULLY_FUNDED`, default milestones generated.
6. Farmer submits milestone proof, lead verifies.
7. Milestone approval releases funds from escrow to farmer wallet/withdrawable balance.
8. Farmer withdraws available balance.
9. Farmer settles project after crop cycle; payouts distributed to investors/farmer/lead.

## Financial Precision
Money fields in `User` and `FarmProject` use `BigDecimal` with `NUMERIC(12,2)` mapping for exact decimal arithmetic:
- `walletBalance`, `escrowBalance`, `currentFunding`, `withdrawableBalance`, `releasedToFarmer`, `finalFarmerProfit`, `targetAmount`

Milestone release is idempotent via `fundsReleased/status` guard in `MilestoneService.approveMilestone(...)`.

## Domain Model (High-Level)
- `User`: identity, role, KYC status, wallet balance
- `FarmProject`: funding target/state, escrow, withdrawable/released balances
- `Investment`: investor contribution, payment status, settlement return
- `Milestone`: release %, verification state, proof, release amount
- `Withdrawal`: payout requests/history
- `Notification`, `ProjectUpdate`: communication timeline

## API Reference (Backend Endpoints)

### Auth (`/api/auth`)
- `POST /signup`
- `POST /login`
- `GET /me`

### Admin / Lead (`/api/admin`)
- `GET /pending-kyc` (`/pending-farmers` alias)
- `GET /pending-projects`
- `POST /approve-project/{id}`
- `GET /pending-milestones`
- `POST /verify-farmer/{id}?approve=true|false`
- `POST|PUT /verify-user/{id}?approve=true|false`

### Projects (`/api/projects`)
- `POST /create` (Farmer)
- `GET /discover?region=&crop=`
- `GET /all`
- `GET /my-projects` (Farmer)
- `GET /user/me` (Farmer legacy route)
- `GET /{id}`
- `GET /active`
- `GET /{id}/milestones`
- `POST /{id}/reconcile` (Farmer/Lead)
- `POST /{id}/settle` (Farmer)

### Investments (`/api/investments`)
- `POST /pay`
- `POST /complete/{id}?txnId=...`
- `GET /portfolio/{investorId}` (Investor; service uses auth user context)
- `GET /my-portfolio`
- `GET /portfolio`
- `GET /my-history`
- `GET /project/{projectId}`

### Milestones (`/api/milestones`)
- `POST /create` (Farmer)
- `POST /{id}/submit` (Farmer)
- `POST /{id}/verify?approved=true|false` (Lead)
- `POST /{id}/upload-proof` (multipart)
- `GET /project/{projectId}`
- `GET /project/{projectId}/summary`

### Dashboard (`/api/dashboard`)
- `GET /farmer/{farmerId}/portfolio`
- `GET /investor/me/portfolio`
- `GET /investor/{investorId}/portfolio`
- `GET /investor/investment/{id}/receipt`
- `GET /lead/portfolio`
- `POST /lead/verify-milestone/{milestoneId}?approved=true|false`

### Other
- KYC: `POST /api/users/upload-kyc`
- Notifications: `GET /api/notifications`
- Project updates: `POST /api/updates/post`, `GET /api/updates/project/{projectId}`
- Withdrawals: `GET /api/withdrawals/project/{projectId}`, `GET /api/withdrawals/my-history`, `POST /api/withdrawals/request`
- Wallet: `POST /api/wallet/withdraw`

## Frontend Integration Notes
- Base URL: `http://localhost:8081`
- Use JSON for most endpoints, multipart for `/api/milestones/{id}/upload-proof`.
- Persist JWT after login and send in `Authorization` header.
- Use role from login response for route guards.
- Primary portfolio/dashboard contracts:
  - Farmer: `/api/dashboard/farmer/{farmerId}/portfolio`
  - Investor: `/api/dashboard/investor/me/portfolio`
  - Lead queue: `/api/dashboard/lead/portfolio`

## Database Notes
- Hibernate auto-update is enabled (`ddl-auto=update`).
- `schema.sql` applies additional column setup and numeric-type alignment.
- Recommended next step: move to versioned migrations for predictable environments.

## Running Tests
```bash
./mvnw test
```

## Known Hardening Gaps (Current Codebase)
- Hardcoded JWT secret.
- Credentials in plain text properties.
- No global exception contract (error payloads can vary).
- Some response DTOs still expose raw entities; introduce dedicated DTOs for stricter API contracts.
