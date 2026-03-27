#!/bin/bash
# ============================================================================
# MiniMart POS Ultimate - Linux / macOS Setup Script
# Run once to configure the database and build the application.
# Usage: chmod +x setup.sh && ./setup.sh
# ============================================================================

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

header() { echo -e "\n${CYAN}=== $1 ===${NC}"; }
ok()     { echo -e "${GREEN}  ✔ $1${NC}"; }
warn()   { echo -e "${YELLOW}  ⚠ $1${NC}"; }
error()  { echo -e "${RED}  ✖ $1${NC}"; exit 1; }
info()   { echo -e "  $1"; }

echo ""
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  MiniMart POS Ultimate - Setup${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# ── Step 1: Check Java ────────────────────────────────────────────────────────
header "1/5 Checking Java 17"
if ! command -v java &>/dev/null; then
    error "Java not found. Install Java 17+ from: https://adoptium.net"
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR=$(echo "$JAVA_VER" | cut -d'.' -f1)
if [ "$JAVA_MAJOR" -lt 17 ] 2>/dev/null; then
    error "Java 17+ required. Found: $JAVA_VER"
fi
ok "Java $JAVA_VER"

# ── Step 2: Check Maven ───────────────────────────────────────────────────────
header "2/5 Checking Maven"
if ! command -v mvn &>/dev/null; then
    warn "Maven not found. Trying to install..."
    if command -v brew &>/dev/null; then
        brew install maven
    elif command -v apt-get &>/dev/null; then
        sudo apt-get install -y maven
    elif command -v yum &>/dev/null; then
        sudo yum install -y maven
    else
        error "Cannot install Maven automatically. Install from: https://maven.apache.org/download.cgi"
    fi
fi
ok "Maven $(mvn -version 2>&1 | head -1 | awk '{print $3}')"

# ── Step 3: Check MySQL ───────────────────────────────────────────────────────
header "3/5 Checking MySQL"
if ! command -v mysql &>/dev/null; then
    warn "MySQL client not found in PATH."
    warn "Database must be set up manually. See SETUP_GUIDE.md"
    SKIP_DB=true
fi
if [ -z "$SKIP_DB" ]; then
    ok "MySQL client found."
fi

# ── Step 4: Database setup ────────────────────────────────────────────────────
header "4/5 Setting up database"

read -p "  MySQL host [localhost]: " DB_HOST
DB_HOST="${DB_HOST:-localhost}"

read -p "  MySQL port [3306]: " DB_PORT
DB_PORT="${DB_PORT:-3306}"

read -s -p "  MySQL root password: " DB_ROOT_PASS
echo ""

if [ -z "$SKIP_DB" ]; then
    info "Creating database user..."
    mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -p"$DB_ROOT_PASS" \
          < scripts/setup/create_db_user.sql 2>/dev/null || true

    info "Importing schema..."
    if mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -p"$DB_ROOT_PASS" \
             < src/main/resources/sql/schema.sql; then
        ok "Database schema imported."
    else
        error "Schema import failed. Check credentials and try again."
    fi
fi

read -s -p "  Password for pos_user [posuser123]: " DB_PASS
echo ""
DB_PASS="${DB_PASS:-posuser123}"

# Write connection.properties
cat > src/main/resources/config/connection.properties << EOF
# MiniMart POS - Database Connection Configuration
db.host=${DB_HOST}
db.port=${DB_PORT}
db.name=minimart_pos
db.user=pos_user
db.password=${DB_PASS}

sync.multicast.group=239.255.1.1
sync.multicast.port=45678
sync.enabled=true
EOF
ok "Connection properties written."

# ── Step 5: Build ──────────────────────────────────────────────────────────
header "5/5 Building application"
info "This takes 2-3 minutes on first run (downloading dependencies)..."
if mvn clean package -q; then
    ok "Build successful."
else
    error "Build failed. Check output above."
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Setup Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "  Default login:"
echo "    Username: admin"
echo "    Password: Admin@123"
echo ""
echo "  To start the application:"
echo "    ./run.sh"
echo ""
