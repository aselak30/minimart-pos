# MiniMart POS Ultimate

**Enterprise-grade Point of Sale system for mini-supermarkets**
Java 17 + JavaFX 21 + MySQL 8 | Maven | Multi-workstation networked

---

## Project Structure

```
minimart-pos/
├── pom.xml                             Maven build file
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java        Java module descriptor
│   │   │   └── com/minimartpos/
│   │   │       ├── app/                Entry points (MainApp, Launcher)
│   │   │       ├── config/             AppConfig, DatabaseConfig
│   │   │       ├── controller/
│   │   │       │   ├── admin/          Admin screen controllers
│   │   │       │   ├── cashier/        Cashier screen controllers
│   │   │       │   └── shared/         Login, DatabaseSetup
│   │   │       ├── model/              Domain models (User, Product, Bill…)
│   │   │       │   └── enums/          Role, Permission, PaymentType
│   │   │       ├── repository/         JDBC data access objects
│   │   │       ├── service/            Business logic layer
│   │   │       ├── security/           SessionManager, PasswordHasher, AuditLogger
│   │   │       ├── util/               SceneManager, AlertUtil, BarcodeUtil…
│   │   │       ├── network/            SyncManager, NetworkMonitor
│   │   │       ├── report/             JasperReports wrappers
│   │   │       └── hardware/           Printer, scanner, cash drawer
│   │   └── resources/
│   │       ├── fxml/
│   │       │   ├── admin/              Admin screen FXML layouts
│   │       │   ├── cashier/            Cashier screen FXML layouts
│   │       │   └── shared/             Login.fxml, DatabaseSetup.fxml
│   │       ├── css/
│   │       │   └── main.css            Global JavaFX stylesheet
│   │       ├── sql/
│   │       │   └── schema.sql          Full database schema + seed data
│   │       └── config/
│   │           ├── connection.properties  DB connection settings
│   │           └── log4j2.xml             Logging configuration
│   └── test/
│       └── java/com/minimartpos/       JUnit 5 test classes
└── docs/                               Architecture docs (add here)
```

---

## Tech Stack

| Component          | Technology            |
|--------------------|-----------------------|
| Language           | Java 17               |
| UI                 | JavaFX 21             |
| Extra UI controls  | ControlsFX 11         |
| Icons              | Ikonli (FontAwesome5) |
| Database           | MySQL 8.0             |
| Connection pool    | HikariCP 5            |
| Password hashing   | BCrypt (favre)        |
| Reporting          | JasperReports 6       |
| Excel              | Apache POI 5          |
| PDF                | iText 7               |
| Barcode            | ZXing 3               |
| Logging            | Log4j2                |
| JSON               | Jackson 2             |
| Testing            | JUnit 5 + Mockito     |
| Build              | Maven 3               |

---

## Prerequisites

- Java 17+ JDK (e.g. [Temurin](https://adoptium.net/))
- MySQL 8.0 server
- Maven 3.8+

---

## Quick Start

### 1. Database Setup

```sql
-- Run as MySQL root
mysql -u root -p < src/main/resources/sql/schema.sql
```

This creates the `minimart_pos` database with all tables and seeds:
- Default admin user: `admin` / `Admin@123` (**change immediately**)
- Default product categories

### 2. Configure Connection

Edit `src/main/resources/config/connection.properties`:

```properties
db.host=localhost
db.port=3306
db.name=minimart_pos
db.user=pos_user
db.password=your_password
```

### 3. Build & Run

```bash
# Build fat JAR
mvn clean package -DskipTests

# Run via Maven (development)
mvn javafx:run

# Run compiled JAR
java -jar target/minimart-pos-1.0.0-SNAPSHOT.jar
```

---

## Default Login

| Field    | Value       |
|----------|-------------|
| Username | `admin`     |
| Password | `Admin@123` |

> ⚠️ Change the admin password immediately after first login.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    JavaFX UI Layer                   │
│   FXML layouts  ←→  Controllers  ←→  CSS styles     │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│                   Service Layer                      │
│  AuthService  ProductService  BillingService  ...    │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│                 Repository Layer                     │
│  UserRepository  ProductRepository  BillRepository   │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│              HikariCP Connection Pool                │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│               MySQL 8.0 Database                     │
│  (Shared across all 4 workstations via LAN)          │
└─────────────────────────────────────────────────────┘
```

---

## Permission System

Admins automatically have **all** permissions.

Cashier permissions are individually configurable per user via the User Management screen.

Permission categories:
- **A. Price & Cost Visibility** — view cost prices, margins, profit
- **B. Cart Modification** — price overrides, negative quantities, free items
- **C. Discount Management** — percentage/fixed/line-item/bill discounts
- **D. Product Management** — add products during billing, stock adjustments
- **E. Customer Management** — add/edit customers, credit approval
- **F. Bill Management** — delete/edit/void/reprint bills
- **G. Advanced** — other cashier reports, cash drawer, EOD reconciliation

See `Permission.java` for the full enum.

---

## Development Roadmap

### Phase 1 — Core ✅ (Scaffold)
- [x] Project structure & Maven config
- [x] Database schema
- [x] Models: User, Product, Bill, BillItem
- [x] Auth service (BCrypt, lockout, CAPTCHA)
- [x] Session manager
- [x] Permission system
- [x] Login screen (FXML + controller)
- [x] HikariCP connection pool
- [x] CSS theme

### Phase 2 — POS Terminal ✅ (Complete)
- [x] `POSTerminal.fxml` — full screen layout (search, category pills, quick grid, cart, totals, payment buttons)
- [x] `POSTerminalController.java` — live clock, product search, barcode input, cart table (inline editing), permission-gated UI, payment routing
- [x] `Payment.fxml` + `PaymentController.java` — cash/card/credit/mobile dialog with quick-amount buttons and change calculation
- [x] `BillingService.java` — add/remove/update items, price override, line & bill discounts, finalize bill, void bill (all permission-checked)
- [x] `BillRepository.java` — atomic transactional save of bill + items, status updates, sequence generation
- [x] `StockService.java` — SELECT FOR UPDATE stock deduction, reversal on void, manual adjustment
- [x] `ProductService.java` — in-memory cache (1 min TTL), search, barcode lookup, category filter, quick-product list
- [x] `CurrencyUtil.java` — format/parse money values
- [x] `AlertUtil.java` — reusable dialog helpers

### Phase 3 — Admin Dashboard ✅ (Complete)
- [x] `AdminDashboard.fxml` — sidebar nav + topbar + KPI cards + BarChart + machine status + low stock table + recent bills table + expiry alerts table
- [x] `AdminDashboardController.java` — live clock, KPI binding, delta badges (↑/↓ vs yesterday), hourly chart, live cashier status cards, all 5 tables, sidebar nav routing, auto-refresh every 60s
- [x] `DashboardService.java` — all DB aggregations: daily KPIs, yesterday deltas, sales-by-hour, low stock, expiring products, recent bills, active shifts
- [x] `DashboardStats.java` — DTO carrying all dashboard metrics
- [x] `AuditService.java` — fire-and-forget audit_log writer
- [x] `UserService.java` — user CRUD, permission save, password reset, audit trail
- [x] `DateUtil.java` — date/time formatting constants and helpers

### Phase 4 — All Major Screens ✅ (Complete)

**Reports** — `Reports.fxml` + `ReportsController` + `ReportService` (6 report types, date range, trend chart, dynamic table)
**Settings** — `Settings.fxml` + `SettingsController` + `SettingsService` + `SettingsRepository`
**Customer Search Modal** — `CustomerSearch.fxml` + `CustomerSearchController` + `CustomerService` + `CustomerRepository`
**End-of-Shift Dialog** — `Shift.fxml` + `ShiftController` + `ShiftRepository`
**Database Setup Screen** — `DatabaseSetup.fxml` + `DatabaseSetupController`
**Models** — `Shift`, `Setting`, `Customer` fully implemented
**POS Terminal** — wired real customer search and shift close dialogs

### Phase 5 — Remaining (future sessions)
- [ ] Excel import/export (Apache POI) in Product Management
- [ ] Receipt printing (PrinterManager + ESC/POS thermal)
- [ ] Barcode label printing (BarcodeUtil + ZXing)
- [ ] Cash drawer trigger
- [ ] PDF export (iText)
- [ ] Audit log viewer, Bill history, Stock adjustment screens
- [ ] Customer admin screen
- [ ] Multi-machine sync (SyncManager)
- [ ] Offline mode

---

## Multi-Machine Setup

1. Install MySQL on the server machine (can be the Admin PC)
2. Configure firewall to allow port 3306 from all workstation IPs
3. Create a MySQL user with access from each workstation IP:
   ```sql
   CREATE USER 'pos_user'@'192.168.1.%' IDENTIFIED BY 'password';
   GRANT ALL ON minimart_pos.* TO 'pos_user'@'192.168.1.%';
   ```
4. Edit `connection.properties` on each workstation with the server's IP
5. Assign each machine a `machine_code` in the `machines` table

---

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AuthServiceTest
```

Tests use H2 in-memory database — no MySQL required for testing.

---

## Logs

Application logs are written to:
```
~/MiniMartPOS/logs/minimart-pos.log   (rolling daily)
~/MiniMartPOS/logs/audit.log          (security events)
```

---

## License

Private/commercial project. All rights reserved.
