# Database

This directory holds **schema design documents** (ER diagrams, modeling notes).
The executable schema lives exclusively in Flyway migrations:

```
server/src/main/resources/db/migration/
```

## Rules

1. Every schema change is a new migration file — never edit an applied migration.
2. Naming: `V{sequence}__{snake_case_description}.sql` (e.g. `V1__create_user_table.sql`).
3. Never modify a production database manually.

## Conventions

- Tables and columns: `snake_case`.
- Every table carries `id` (BIGINT, snowflake), `created_at`, `updated_at`,
  `deleted` (TINYINT logical-delete flag) — mapped by `BaseEntity`.
- Charset `utf8mb4`, collation `utf8mb4_unicode_ci`.
- Foreign keys are modeled logically (indexed columns), not as physical constraints,
  to keep migrations and scaling simple.
