# removing-manual-steps

A Java command-line application that allows multiple users to list items, browse items listed by others, and manage rentals. The application uses SQLite for persistent storage and BCrypt password hashing for account security.

## Features

- User registration and login.
- Secure password hashing using BCrypt.
- Create, view and manage personal item listings.
- Browse available items belonging to other users.
- Paginated inventory and item browsing.
- Rent items listed by other users.
- Owner-controlled return confirmation.
- Input validation and application-level error handling.
- Structured application logging.
- SQLite database persistence.
- Demo database seeding.
- Docker support with persistent database storage.

## Architecture

The application follows a layered structure that separates user interaction, business logic, data access and infrastructure responsibilities.

| Package | Responsibility |
|---|---|
| `domain` | Core application models, item and rental statuses, and validation rules. |
| `repository` | Database access and persistence operations for users, items and rentals. |
| `service` | Business logic, application workflows and ownership enforcement. |
| `security` | Password hashing and password verification. |
| `infrastructure` | Database connection management, demo data seeding and structured logging. |
| `transport` | Command-line interface and interaction with users. |

`Main.java` is the application entry point and coordinates application startup.

## Business Rules

The application enforces the following rules:

- Users must register and log in before accessing authenticated functionality.
- Users can manage their own item listings.
- Users cannot rent their own items.
- Only available items belonging to other users can be rented.
- Item ownership is checked before listing-management operations.
- Only the item owner can confirm an item's return.
- Input and domain validation are performed before relevant operations are processed.
- Passwords are hashed before storage and are never stored as plain text.

## Requirements

- Java 25 and Maven 3.9 or later to build or test locally.
- Docker with the Compose plugin to run the containerized app.

## Build and Test

Run the full build and test suite:

```bash
mvn clean verify
```

The same command runs in the CI pipeline on every push and pull request. CI uploads the JaCoCo HTML and XML reports as the `jacoco-coverage-report` workflow artifact.
The workflow checks out the source, installs Java 25 with Maven dependency caching, runs `clean verify`, and then uploads the coverage report.

Run tests without packaging:

```bash
mvn test
```

## Run with Docker Compose

Start the app from the repository root:

```bash
docker compose up
```

Compose builds the image from the existing `Dockerfile` and starts the interactive CLI. Use `docker compose up --build` to force a fresh image build. Press Ctrl+C to stop the app.

Compose sets `DATABASE_PATH` and `LOG_PATH` and mounts two named volumes:

| Volume | Contents |
|---|---|
| `rental-tracker-data` | SQLite database at `/opt/rental-tracker/data/app.db` |
| `rental-tracker-logs` | Application log at `/opt/rental-tracker/logs/app.log` |

Both volumes survive `docker compose down`, container restarts, and image rebuilds. To remove the persisted database and logs as well, run `docker compose down --volumes`.

## Protect the Main Branch

Configure branch protection for `main` in the repository hosting settings:

1. Require changes to arrive through pull requests and disallow direct pushes to `main`.
2. Require the status check **Rental Tracker CI / Build and test** before merging.
3. Ensure pull request pages display this check so reviewers can see whether it passed without opening the workflow logs.

The required check is reported by the `Rental Tracker CI` workflow's `Build and test` job.

## Run the Executable JAR

Launch the executable JAR:

```bash
java -jar target/removing-manual-steps-1.0.jar
```

The application initializes its SQLite database automatically and writes logs to `logs/app.log`.

### Database Configuration

By default, the database is created at:

```text
data/app.db
```

You can configure a different database location using the `DATABASE_PATH` environment variable:

```bash
DATABASE_PATH=/path/to/database.db java -jar target/removing-manual-steps-1.0.jar
```

Alternatively, pass the database path as the first JAR argument:

```bash
java -jar target/removing-manual-steps-1.0.jar /path/to/database.db
```

When both options are supplied, the command-line argument takes precedence over the environment variable.

Parent directories are created automatically when needed.

Set `LOG_PATH` to choose another application log file location.

## Optional Demo Data

The `SeedData` utility creates a demonstration database containing four users and sample items.

Demo accounts:

| Username | Password |
|---|---|
| `siim` | `password123` |
| `meelis` | `password123` |
| `liisa` | `password123` |
| `marili` | `password123` |

Generate the demo database using:

```bash
mvn package
java -cp target/removing-manual-steps-1.0.jar tech.kood.rental.infrastructure.SeedData /path/to/demo.db
```

**Important:** Running `SeedData` against an existing database path replaces that database file. Use a new path or back up existing data before running the utility.

The demo credentials are intended for development and demonstration purposes only.

## Code Coverage

The latest recorded full verification completed with:

- **295 tests**
- **0 failures**
- **0 errors**
- **0 skipped tests**

### JaCoCo Coverage Results

| Metric | Coverage |
|---|---:|
| Instruction coverage | 94.37% |
| Line coverage | 92.63% |
| Branch coverage | 88.11% |
| Method coverage | 99.41% |
| Class coverage | 100% |

JaCoCo generates the following reports:

- [HTML coverage report](coverage/index.html)
- [XML coverage report](coverage/jacoco.xml)

The reported figures reflect measured coverage. The project does not claim complete instruction, line or branch coverage.

## Database Design

The database uses SQLite to persist user accounts, item listings and rental records.

See [ERD.md](ERD.md) for the entity relationship diagram and documentation of the database structure, relationships and ownership rules.

## Project Structure

```text
src/
└── main/
    ├── java/
    │   └── tech/kood/rental/
    │       ├── Main.java
    │       ├── domain/
    │       ├── infrastructure/
    │       ├── repository/
    │       ├── security/
    │       ├── service/
    │       └── transport/
    └── resources/
        └── schema.sql

src/test/
└── java/
    └── tech/kood/rental/

coverage/
├── index.html
└── jacoco.xml

ERD.md
Dockerfile
pom.xml
```
