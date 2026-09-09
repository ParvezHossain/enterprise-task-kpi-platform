# Database

## Auth identity schema

The repository did not yet contain TICKET-0102 migrations when TICKET-0103 began.
`V1__identity_schema.sql` supplies the identity prerequisite: `users`, `roles`,
`permissions`, `user_roles`, and `role_permissions`. OAuth registered-client,
authorization, and consent tables remain for TICKET-0102; this change does not
claim that ticket is complete. Later schema migrations must use new versions.

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

Version 1.1 follows schema version 1 and leaves versions 2 and 3 available for
future schema work. Flyway applies each version once and verifies its checksum;
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
