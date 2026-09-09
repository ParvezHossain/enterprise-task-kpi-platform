# Database

## Auth identity schema

The repository did not yet contain TICKET-0102 migrations when TICKET-0103 began.
`V1__identity_schema.sql` supplies the identity prerequisite: `users`, `roles`,
`permissions`, `user_roles`, and `role_permissions`. TICKET-0105 adds the missing OAuth registered-client, authorization, and consent
tables in V2. Later schema migrations must use new versions.

All IDs are UUIDs. Hibernate generates IDs for persisted entities; development
reference rows use fixed IDs. Users have canonical lowercase, trimmed unique
emails, an encoded password field, an enabled flag, and an optimistic-lock version.
Password encoding and full registration validation belong to TICKET-0104; entities
must receive encoded passwords, and are never API request/response models.

Role names are constrained to ADMIN, PROJECT_MANAGER, TEAM_LEADER, and EMPLOYEE.
Permission names are unique strings so the permission catalog can evolve through
migrations. User-to-role and role-to-permission mappings are lazy, owning-side
many-to-many relationships, without cascading deletion of shared reference rows.
Join-table primary keys prevent duplicate memberships; foreign keys prevent
orphaned associations. Reverse foreign-key indexes support reference lookups and
constraint checks. Unique email/name constraints supply their lookup indexes.

The migration grants runtime DML on these five tables to Flyway's `runtimeRole`
placeholder, mapped to `AUTH_DB_USERNAME`. Supply a conventional PostgreSQL role
name without embedded quotes. Flyway uses the separate migration login and keeps
schema ownership; the runtime login receives no schema creation privileges.
Hibernate remains configured with `ddl-auto=validate` in every profile.

## Production schema versus development data

- `db/migration/schema`: production schema migrations, loaded in every profile.
- `db/migration/dev-test`: explicitly labeled development/test data migrations,
  loaded only when `dev` or `test` is active and `prod` is not active.
- `V1_1__dev_test_identity_seed.sql`: four roles only; no users, passwords, clients,
  or role-permission assignments are automatically granted.

`local` and `docker` choose database connection defaults and do not enable seeds.
Use `local,dev` for seeded local development or `docker,dev` for a development
container. Production must use its own database and schema-only migration location;
`docker,prod` is the intended production profile combination. Never reuse or promote
a seeded development database into production. Removing the dev profile does not
remove previously applied data; Flyway's missing-migration validation can also
reject such a switch. Do not enable out-of-order execution or ignore validation
to work around environment mixing.

Version 1.1 follows identity schema version 1. OAuth schema version 2 is followed
by development client data version 2.1; version 3 remains available. Flyway applies each version once and verifies its checksum;
reference data changes must be new migrations, never edits to an applied file.

## Repository boundary and verification

Repositories extend Spring Data `Repository` and expose only save, count, and
unique-key/ID lookups. There is no unbounded `findAll` API. Future collection
queries must use bounded pagination. Associations are read inside transactions;
Open Session in View remains disabled. No REST controllers expose these entities.

Testcontainers tests validate the schema with Hibernate, query all four seed roles,
persist/reload user-role-permission associations, reject duplicate canonical email,
and verify repeated migration does not reapply data. A separate fresh database
with `prod`, `dev`, and `test` simultaneously active confirms production excludes
the seed location and leaves all reference tables empty.

## Registration writes (TICKET-0104)

Registration reuses V1 without modifying applied migrations. The bounded,
parameterized user insert uses PostgreSQL `ON CONFLICT (email) DO NOTHING` against
`uk_users_email`; a zero row count becomes a safe duplicate-registration error.
UUID creation, enabled=true, and version=0 are explicit for the native insert.
The service loads that single user and assigns EMPLOYEE through JPA in the same
transaction, so user and membership commit or roll back together. Concurrent
canonical-email attempts produce exactly one account and one rejected attempt.
Production must provision approved EMPLOYEE reference data before using this
service; dev/test seed isolation is unchanged.


## OAuth persistence and client seeds (TICKET-0105)

`V2__oauth2_schema.sql` provides all three Spring Security 7.1.1 JDBC tables using
PostgreSQL `text` for upstream blob fields and `timestamptz` for instants. Client ID
is unique; grants/consents reference registered clients. Hash indexes support exact
large-token comparisons without PostgreSQL btree entry-size failures; state,
principal and client lookups have ordinary indexes. Token lookups return at most
two rows and fail closed on ambiguity. No unbounded list/export API exists.

Runtime credentials can SELECT registered clients and read/write grants/consents;
only migration/provisioning credentials can mutate registered-client definitions.
V2.1 lives exclusively under `dev-test` and inserts `task-management-ui` and
`kpi-ui` using `${taskClientSecretHash}` and `${kpiClientSecretHash}` placeholders.
Values are prefixed Argon2id hashes supplied through environment configuration.
Client settings JSON matches the current framework Jackson 3 serialization format.
No real user or plaintext credential is included in these migrations.

Retain the original generated credentials for an existing dev/test database.
V2.1 is a versioned migration and runs once: changing placeholder environment
values does not update the stored client secrets. Regenerating local material
would create new HTTP credentials that no longer match the database. Use a new
migration for planned client changes/rotation; never edit or repair history to
conceal changed seeds. Newly generated material is intended for a fresh local DB.

The explicit development utility creates a separate, private bootstrap-user SQL
file for the manual walkthrough. It is not a migration or startup seed and must
never run on a production DB. It atomically provisions one EMPLOYEE after dev
reference roles exist, without overwriting existing accounts.
