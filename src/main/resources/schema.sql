-- Accounts for the rental tracker.
-- Other tables (items, rentals) will be added below by whoever builds them.
CREATE TABLE IF NOT EXISTS users (
                                     id          INTEGER PRIMARY KEY AUTOINCREMENT,   -- the database numbers each user itself
                                     username    TEXT NOT NULL UNIQUE                 -- a taken username is rejected by the database
                                     CHECK (length(trim(username)) > 0),  -- and a blank one is refused too
    password    TEXT NOT NULL,                       -- the bcrypt hash, never the plain password
    created_at  TEXT NOT NULL                        -- when the account was made
    DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now'))
    );

-- Things people lend out. Each item belongs to the user who listed it.
CREATE TABLE IF NOT EXISTS items (
                                     id            INTEGER PRIMARY KEY AUTOINCREMENT,       -- the database numbers each item itself
                                     owner_id      INTEGER NOT NULL REFERENCES users(id),   -- who listed it, must be a real user
    name          TEXT NOT NULL
    CHECK (length(trim(name)) > 0),          -- a blank name is refused
    description   TEXT NOT NULL,
    cost_per_day  REAL NOT NULL
    CHECK (cost_per_day >= 0),               -- a negative cost is refused
    status        TEXT NOT NULL DEFAULT 'AVAILABLE'
    CHECK (status IN ('AVAILABLE', 'RENTED', 'UNLISTED'))
    );

-- One row for every time someone borrows an item.
CREATE TABLE IF NOT EXISTS rentals (
                                       id           INTEGER PRIMARY KEY AUTOINCREMENT,        -- the database numbers each rental itself
                                       item_id      INTEGER NOT NULL REFERENCES items(id),    -- which item was borrowed
    renter_id    INTEGER NOT NULL REFERENCES users(id),    -- who borrowed it
    days         INTEGER NOT NULL
    CHECK (days >= 1),                        -- 0 or negative days are refused
    rented_at    TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now')),
    returned_at  TEXT                                      -- empty until the owner confirms the return
    );