# AgriInvest Platform Backend

Spring Boot backend for a role-based agri-investment platform where investors fund farmer projects, village leads verify operational milestones/KYC, and settlements distribute final returns.

## ?? Recent Major Updates
- **Unified Transaction Ledger:** Implemented an immutable audit trail (`TransactionRecord`) tracking all Deposits, Investments, and Withdrawals.
- **Financial Precision:** Migrated all currency fields to `BigDecimal` for high-precision accounting (Atomic settlement logic).
- **Security Hardening:** Enforced role-based access control (RBAC) and idempotency keys for critical financial operations.
- **KYC State Machine:** Upgraded onboarding to a 4-stage regulatory pipeline (PENDING, SUBMITTED, APPROVED, REJECTED).

## Tech Stack
- Java 21, Spring Boot 3.2.4
- Spring Web, Spring Security (JWT), Spring Data JPA
- PostgreSQL
- Lombok, Maven

## Project Structure
```
src/main/java/com/agriinvest/platform
  +- controller/      # REST APIs (Inc. Transaction Ledger)
  +- service/         # Business logic (Precision settlement)
  +- entity/          # JPA domain models (BigDecimal mapped)
  +- repository/      # Data access (Transaction auditing)
  +- security/        # JWT utils/filter
  +- config/          # Security configuration
```

## Getting Started
1. Install Java 21 and PostgreSQL.
2. Create database `agri_invest`.
3. Configure environment variables for production (see Configuration).
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

**Security Notice:** In production, use environment variables for `SPRING_DATASOURCE_PASSWORD` and `JWT_SECRET_KEY`.

## Roles & Governance
- `INVESTOR`: Browse projects, fund wallets, track portfolio, export ledger.
- `FARMER`: Create projects, upload milestone proofs, withdraw profits.
- `VILLAGE_LEAD`: Verify KYC, approve projects, validate milestone completion.

## ?? Unified Transaction Ledger
The platform now includes an immutable auditing layer:
- **Automatic Logging:** `DEPOSIT`, `INVESTMENT`, and `WITHDRAWAL` types are recorded atomically within service transactions.
- **Reference IDs:** Every transaction carries a unique UUID or payment gateway reference.
- **API Access:** `GET /api/transactions/my` provides a complete financial timeline.
- **Export Ready:** Data structures are optimized for mobile-side CSV export.

## ?? Financial Precision
Money fields use `BigDecimal` with `NUMERIC(18,2)` mapping for exact decimal arithmetic:
- `walletBalance`, `escrowBalance`, `currentFunding`, `withdrawableBalance`, `targetAmount`

## API Reference (Key Endpoints)

### ?? Transactions & Wallet
- `GET /api/transactions/my` - Fetch personalized audit trail.
- `POST /api/wallet/add-funds` - Deposit money into apps wallet.
- `POST /api/withdrawals/request` - Initiate farmer profit payout.

### ??? Auth & KYC
- `POST /api/auth/signup` | `POST /api/auth/login`
- `POST /api/users/upload-kyc` - Submit Aadhaar/Land documents.
- `GET /api/admin/pending-kyc` - Lead review queue.

### ?? Projects & Investments
- `POST /api/investments/pay` - Initiate investment.
- `POST /api/investments/complete/{id}` - Finalize with Idempotency Key.
- `POST /api/projects/settle` - Trigger final profit distribution.

## Running Tests
Ensure 100% green status before deployment:
```bash
./mvnw test
```

## Database Notes
- `schema.sql` ensures numeric-type alignment for high-precision balances.
- Migrated to `DECIMAL(18,2)` for all financial ledgers.
