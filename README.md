# Multi-User Rental Tracker

A Java command-line application for users to list items, browse and rent items
from other users, and confirm item returns. It uses SQLite for persistence and
BCrypt password hashing.

## Requirements

- Java 25
- Maven 3.9 or later
- Docker (optional)

## Build, test, and run

Run the full test suite and build the executable JAR:

```sh
mvn clean verify
```

Launch the application:

```sh
java -jar target/multi-user-rental-tracker-1.0.jar
```

The database is initialized automatically at `data/app.db`. Set
`DATABASE_PATH` to use another location, or pass a database path as the first
JAR argument; the argument takes precedence over the environment variable.
Parent directories are created as needed.

The CLI supports registration and login, item listing and management, paged
inventory and browsing, rentals of other users' available items, and owner-only
return confirmation. Passwords are hashed before they are stored.

### Optional demo data

`SeedData` can create a demo database with four users and sample items. The
demo usernames are `siim`, `meelis`, `liisa`, and `marili`; their password is
`password123`.

**Warning:** running `SeedData` against an existing path replaces that database
file. Back up any database you want to keep, or use a new path:

```sh
mvn package
java -cp target/multi-user-rental-tracker-1.0.jar \
  tech.kood.rental.infrastructure.SeedData /path/to/demo.db
```

## Docker

Build the image:

```sh
docker build -t multi-user-rental-tracker .
```

Run it with a named volume so the database survives container removal:

```sh
docker volume create rental-tracker-data
docker run --rm -it \
  -v rental-tracker-data:/opt/rental-tracker/data \
  multi-user-rental-tracker
```

The container stores its database at
`/opt/rental-tracker/data/app.db`. Set `DATABASE_PATH` to change the database
path inside the container.

## Tests and coverage

Run tests with `mvn test`; `mvn verify` also packages the application. JaCoCo
generates the tracked HTML report at [`coverage/index.html`](coverage/index.html)
and XML report at [`coverage/jacoco.xml`](coverage/jacoco.xml).

The latest full verification (`mvn clean verify`) reports 295 tests passing,
with 0 failures, 0 errors, and 0 skipped tests.

The JaCoCo report records 94.37% instruction coverage, 92.63% line coverage,
88.11% branch coverage, 99.41% method coverage, and 100% class coverage.
These figures are reported as measured; the project does not claim 100%
instruction, line, or branch coverage.

## Database design

See [`ERD.md`](ERD.md) for the users, items, and rentals schema and ownership
rules.
