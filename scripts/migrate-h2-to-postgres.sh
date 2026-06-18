#!/usr/bin/env bash
# H2 파일 DB 데이터를 PostgreSQL로 마이그레이션
#
# 사용법:
#   1) 마이그레이션용 override로 PostgreSQL 기동 (호스트 포트 오픈):
#      docker compose -f docker-compose.yml -f docker-compose.migrate.yml up -d postgres
#   2) 스크립트 실행:
#      bash scripts/migrate-h2-to-postgres.sh
#   3) postgres 재기동으로 포트 닫기 (web-monitor는 영향 없음):
#      docker compose up -d postgres
#
# 환경변수 (미설정 시 기본값 사용):
#   H2_FILE       H2 파일 경로 (확장자 제외), 기본값: ./data/monitor
#   H2_USER       H2 사용자, 기본값: sa
#   H2_PASSWORD   H2 패스워드, 기본값: (빈값)
#   DB_HOST       PostgreSQL 호스트, 기본값: localhost
#   DB_PORT       PostgreSQL 포트, 기본값: 5432
#   DB_NAME       PostgreSQL DB명, 기본값: webmonitor
#   DB_USERNAME   PostgreSQL 사용자, 기본값: webmonitor
#   DB_PASSWORD   PostgreSQL 패스워드, 기본값: webmonitor

set -euo pipefail

# ── 설정값 ─────────────────────────────────────────────────────────────────────
H2_FILE="${H2_FILE:-./data/monitor}"
H2_USER="${H2_USER:-sa}"
H2_PASSWORD="${H2_PASSWORD:-}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-webmonitor}"
DB_USERNAME="${DB_USERNAME:-webmonitor}"
DB_PASSWORD="${DB_PASSWORD:-webmonitor}"

EXPORT_DIR="${TMPDIR:-/tmp}/h2_migration_$$"
TABLES=(settings sites keywords products alerts articles)

# ── 색상 출력 ────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── 사전 검사 ────────────────────────────────────────────────────────────────
check_prerequisites() {
    info "사전 조건 확인 중..."

    # H2 파일 존재 확인
    if [[ ! -f "${H2_FILE}.mv.db" ]]; then
        error "H2 파일을 찾을 수 없습니다: ${H2_FILE}.mv.db"
        error "H2_FILE 환경변수를 확인하거나 프로젝트 루트 디렉토리에서 실행하세요."
        exit 1
    fi
    info "H2 파일 확인: ${H2_FILE}.mv.db"

    # H2 JAR 탐색 (Maven 로컬 저장소)
    H2_JAR=$(find "${HOME}/.m2/repository/com/h2database/h2" -name "h2-*.jar" \
             ! -name "*sources*" ! -name "*javadoc*" 2>/dev/null | sort -V | tail -1)
    if [[ -z "${H2_JAR}" ]]; then
        error "H2 JAR를 Maven 로컬 저장소에서 찾을 수 없습니다."
        error "mvn dependency:resolve 또는 ./mvnw dependency:resolve 를 실행하세요."
        exit 1
    fi
    info "H2 JAR 발견: ${H2_JAR}"

    # psql 존재 확인
    if ! command -v psql &>/dev/null; then
        error "psql 클라이언트가 설치되지 않았습니다."
        error "Ubuntu: sudo apt-get install postgresql-client"
        exit 1
    fi

    # PostgreSQL 접속 확인
    if ! PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" \
         -U "${DB_USERNAME}" -d "${DB_NAME}" -c "SELECT 1" &>/dev/null; then
        error "PostgreSQL 접속 실패: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
        error "PostgreSQL이 기동 중인지 확인하세요: docker compose -f docker-compose.yml -f docker-compose.migrate.yml up -d postgres"
        exit 1
    fi
    info "PostgreSQL 접속 확인: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
}

# ── H2 → CSV Export ──────────────────────────────────────────────────────────
export_h2_to_csv() {
    info "H2 데이터 CSV export 시작..."
    mkdir -p "${EXPORT_DIR}"

    # CSVWRITE SQL 스크립트 생성
    local sql_script="${EXPORT_DIR}/export.sql"
    cat > "${sql_script}" <<SQL
CALL CSVWRITE('${EXPORT_DIR}/settings.csv', 'SELECT * FROM SETTINGS');
CALL CSVWRITE('${EXPORT_DIR}/sites.csv', 'SELECT * FROM SITES');
CALL CSVWRITE('${EXPORT_DIR}/keywords.csv', 'SELECT * FROM KEYWORDS');
CALL CSVWRITE('${EXPORT_DIR}/products.csv', 'SELECT * FROM PRODUCTS');
CALL CSVWRITE('${EXPORT_DIR}/alerts.csv', 'SELECT * FROM ALERTS');
CALL CSVWRITE('${EXPORT_DIR}/articles.csv', 'SELECT * FROM ARTICLES');
SQL

    # brace group은 항상 exit 0 (grep no-match 억제).
    # pipefail이 java 실패를 전파하므로 set -e가 정상 동작하고 에러 출력도 실시간 표시됨.
    java -cp "${H2_JAR}" org.h2.tools.RunScript \
        -url "jdbc:h2:file:${H2_FILE}" \
        -user "${H2_USER}" \
        -password "${H2_PASSWORD}" \
        -script "${sql_script}" \
        -showResults 2>&1 | { grep -v "^$" || true; }

    for table in "${TABLES[@]}"; do
        local csv_file="${EXPORT_DIR}/${table}.csv"
        if [[ ! -f "${csv_file}" ]]; then
            warn "${table} 테이블 CSV가 생성되지 않았습니다 (데이터 없음으로 간주)."
        else
            local row_count
            row_count=$(( $(wc -l < "${csv_file}") - 1 ))  # 헤더 제외
            (( row_count < 0 )) && row_count=0
            info "  ${table}: ${row_count}행 export"
        fi
    done
}

# ── CSV → PostgreSQL Import ───────────────────────────────────────────────────
import_csv_to_postgres() {
    info "PostgreSQL import 시작..."

    # 확인 프롬프트: PostgreSQL 데이터를 모두 삭제 후 재import
    warn "PostgreSQL DB의 기존 데이터를 모두 삭제하고 H2 데이터로 교체합니다."
    read -r -p "계속하려면 'yes'를 입력하세요: " confirm
    if [[ "${confirm}" != "yes" ]]; then
        info "취소되었습니다."
        exit 0
    fi

    # FK 체크 비활성화 후 일괄 import — SET LOCAL로 트랜잭션 종료 시 자동 복원
    local import_sql="${EXPORT_DIR}/import.sql"
    cat > "${import_sql}" <<SQL
BEGIN;
SET LOCAL session_replication_role = 'replica';

-- 멱등성 보장: 재실행 시 PK 충돌 방지
-- CASCADE: replica 모드는 trigger 기반 FK만 억제하므로 TRUNCATE의 catalog 레벨 FK 체크는 별도로 CASCADE가 필요
TRUNCATE TABLE articles, alerts, keywords, products, sites, settings CASCADE;

SQL

    for table in "${TABLES[@]}"; do
        local csv_file="${EXPORT_DIR}/${table}.csv"
        if [[ -f "${csv_file}" ]]; then
            local row_count
            row_count=$(( $(wc -l < "${csv_file}") - 1 ))
            if (( row_count > 0 )); then
                cat >> "${import_sql}" <<SQL
\COPY ${table} FROM '${csv_file}' CSV HEADER;
SQL
            fi
        fi
    done

    # 시퀀스 재조정 (빈 테이블 포함 모든 경우를 정확히 처리)
    cat >> "${import_sql}" <<SQL

-- BIGSERIAL 시퀀스 재조정 (SET LOCAL로 replica 모드는 COMMIT 시 자동 복원됨)
-- setval(seq, n, false): is_called=false → 다음 nextval()이 n을 직접 반환.
-- 빈 테이블: COALESCE(NULL+1, 1)=1 → next=1 / 데이터 있음: MAX(id)+1 → next=MAX(id)+1
SELECT setval(pg_get_serial_sequence('settings', 'id'),  COALESCE(MAX(id)+1, 1), false) FROM settings;
SELECT setval(pg_get_serial_sequence('sites', 'id'),     COALESCE(MAX(id)+1, 1), false) FROM sites;
SELECT setval(pg_get_serial_sequence('keywords', 'id'),  COALESCE(MAX(id)+1, 1), false) FROM keywords;
SELECT setval(pg_get_serial_sequence('products', 'id'),  COALESCE(MAX(id)+1, 1), false) FROM products;
SELECT setval(pg_get_serial_sequence('alerts', 'id'),    COALESCE(MAX(id)+1, 1), false) FROM alerts;
SELECT setval(pg_get_serial_sequence('articles', 'id'),  COALESCE(MAX(id)+1, 1), false) FROM articles;

COMMIT;
SQL

    PGPASSWORD="${DB_PASSWORD}" psql \
        -h "${DB_HOST}" -p "${DB_PORT}" \
        -U "${DB_USERNAME}" -d "${DB_NAME}" \
        -v ON_ERROR_STOP=1 \
        -f "${import_sql}"

    info "import 완료."
}

# ── 결과 검증 ────────────────────────────────────────────────────────────────
verify_migration() {
    info "마이그레이션 결과 검증 중..."
    PGPASSWORD="${DB_PASSWORD}" psql \
        -h "${DB_HOST}" -p "${DB_PORT}" \
        -U "${DB_USERNAME}" -d "${DB_NAME}" \
        -c "SELECT
              'settings' AS table_name, COUNT(*) AS rows FROM settings
            UNION ALL SELECT 'sites',    COUNT(*) FROM sites
            UNION ALL SELECT 'keywords', COUNT(*) FROM keywords
            UNION ALL SELECT 'products', COUNT(*) FROM products
            UNION ALL SELECT 'alerts',   COUNT(*) FROM alerts
            UNION ALL SELECT 'articles', COUNT(*) FROM articles;"
}

# ── 정리 ─────────────────────────────────────────────────────────────────────
cleanup() {
    if [[ -d "${EXPORT_DIR}" ]]; then
        rm -rf "${EXPORT_DIR}"
        info "임시 파일 정리 완료: ${EXPORT_DIR}"
    fi
}

# ── 메인 ─────────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo "================================================="
    echo "  H2 → PostgreSQL 데이터 마이그레이션"
    echo "  H2:         ${H2_FILE}.mv.db"
    echo "  PostgreSQL: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
    echo "================================================="
    echo ""

    trap cleanup EXIT

    check_prerequisites
    export_h2_to_csv
    import_csv_to_postgres
    verify_migration

    echo ""
    info "마이그레이션 완료! 다음 순서로 마무리하세요:"
    info "  1) 포트 닫기:  docker compose up -d postgres"
    info "  2) 앱 기동:    docker compose up -d web-monitor"
    echo ""
}

main "$@"
