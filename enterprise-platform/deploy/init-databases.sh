#!/bin/sh
set -eu
create_database() {
 psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v database="$1" -v runtime="$2" -v runtime_password="$3" -v migration="$4" -v migration_password="$5" <<'SQL'
CREATE ROLE :"runtime" LOGIN PASSWORD :'runtime_password';
CREATE ROLE :"migration" LOGIN PASSWORD :'migration_password';
CREATE DATABASE :"database" OWNER :"migration";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"runtime", :"migration";
SQL
 psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$1" -v runtime="$2" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"runtime";
SQL
}
create_database auth_db auth_runtime "$AUTH_DB_PASSWORD" auth_migration "$AUTH_DB_MIGRATION_PASSWORD"
create_database task_db task_runtime "$TASK_DB_PASSWORD" task_migration "$TASK_DB_MIGRATION_PASSWORD"
create_database kpi_db kpi_runtime "$KPI_DB_PASSWORD" kpi_migration "$KPI_DB_MIGRATION_PASSWORD"
