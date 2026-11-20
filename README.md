# Spring API

A Java 25 / Spring Boot 3.5 backend for the TechGeekPrice admin application, organized with Spring Modulith. The current deployed demo focuses on admin identity, permissions, and category management.

- [Live admin demo](https://categorylisting.ferozfaiz.com)
- [TechGeekPrice frontend source](https://github.com/nartix/techgeekprice-source)

The backend also contains catalogue, product, merchant, and price APIs. A complete public shopping experience and Amazon integration are future work.

## Features

- Separate regular and admin browser sessions, CSRF protection, OAuth2 authorization, and scoped access checks.
- User, role, and permission management with session audit events.
- Hierarchical categories with move/reorder operations, pagination, search, and multiple category-system registrations.
- PostgreSQL persistence, Liquibase migrations, localized messages, OpenAPI, and health/metrics endpoints.
- Unit, MVC, architecture, security, and PostgreSQL integration tests.

See [module architecture](docs/architecture.md) and the runnable [HTTP examples](docs/api/http/).

## Build and test

Requirements: JDK 25. The Gradle wrapper downloads the declared Gradle version and dependencies. Docker must be available for Testcontainers integration tests; these tests create their own disposable services.

```sh
./gradlew testClasses bootJar
./gradlew test
```

Formatting uses Node.js 22 or newer and npm:

```sh
npm ci
./gradlew formatCheck
./gradlew format
```

## Run locally

Use a disposable PostgreSQL database. The development profile applies the included Liquibase migrations to the configured database on startup.

```sh
docker compose up -d postgres
./gradlew bootRun
```

The example Compose service exposes PostgreSQL only on localhost. The default development connection is `localhost:5432/app`, using the local-only `app` username/password. Override `APP_POSTGRES_HOST`, `APP_POSTGRES_PORT`, `APP_POSTGRES_DATABASE`, `APP_POSTGRES_USERNAME`, and `APP_POSTGRES_PASSWORD` for your own database. `.env.example` documents common overrides; the application does not automatically load `.env` files.

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`. The frontend runs separately and defaults to `http://localhost:3000` for OAuth callbacks.

To create an initial local administrator, explicitly set `APP_BOOTSTRAP_ADMIN_ENABLED=true`, `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_EMAIL`, and `APP_BOOTSTRAP_ADMIN_PASSWORD` in your shell before starting the application. Disable bootstrap after creating the account. Keep credentials outside source control.

`application-local.properties` is an optional, ignored local override file. Development uses generated signing keys and clearly marked local secrets. Production requires the `prod` profile, secure cookies, external credentials, explicit signing keys, and valid OAuth URLs; the development configuration is intended for local use.

## License

[MIT](LICENSE). Copyright (c) 2026 Feroz.
