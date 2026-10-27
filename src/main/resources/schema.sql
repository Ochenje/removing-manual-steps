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