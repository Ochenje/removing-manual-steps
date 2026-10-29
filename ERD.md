# Entity-Relationship Diagram

Three tables: `users`, `items`, `rentals`.

```mermaid
erDiagram
    users ||--o{ items : "owns (items.owner_id)"
    users ||--o{ rentals : "rents (rentals.renter_id)"
    items ||--o{ rentals : "is rented in (rentals.item_id)"

    users {
        INTEGER id PK "AUTOINCREMENT"
        TEXT username UK "NOT NULL, UNIQUE, not blank"
        TEXT password "NOT NULL, bcrypt hash, never plain text"
        TEXT created_at "NOT NULL, default current time"
    }

    items {
        INTEGER id PK "AUTOINCREMENT"
        INTEGER owner_id FK "NOT NULL, references users.id"
        TEXT name "NOT NULL, not blank"
        TEXT description "NOT NULL"
        INTEGER cost_per_day "NOT NULL, CHECK >= 0"
        TEXT status "NOT NULL, AVAILABLE or RENTED or UNLISTED"
    }

    rentals {
        INTEGER id PK "AUTOINCREMENT"
        INTEGER item_id FK "NOT NULL, references items.id"
        INTEGER renter_id FK "NOT NULL, references users.id"
        INTEGER days "NOT NULL, CHECK >= 1"
        TEXT rented_at "NOT NULL, default current time"
        TEXT returned_at "NULL until the owner confirms the return"
    }
```

## Status

| Table | State |
|---|---|
| `users` | Implemented in `schema.sql` (merged to `main`). |
| `items` | **Proposal.** Columns to be agreed by the team. |
| `rentals` | **Proposal.** Columns to be agreed by the team. |

## Rules the schema enforces

- `users.username` is UNIQUE, so a taken name is rejected by the database.
- `items.owner_id` and `rentals.renter_id` point at `users.id`, and `rentals.item_id` points at `items.id`.
- A rental needs at least 1 day, and an item cannot have a negative cost.
- An item's `status` says whether it can be rented (AVAILABLE), is out (RENTED), or is hidden by its owner (UNLISTED).
- Ownership (only the owner can list, delist, relist and confirm a return) is checked in the service layer, not in the database.