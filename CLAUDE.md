# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules (run from project root)
mvn clean install

# Run the application (from finance-tool-main directory or root)
mvn spring-boot:run -pl finance-tool-main

# Run single module
mvn clean install -pl finance-tool-service -am
```

The app starts on port 6789 with context path `/api`.

**Prerequisites:** Java 9+, MySQL on localhost:3306 with database `finance`.

## Architecture

Multi-module Maven project (Spring Boot 3.5.5) for a finance tools backend service.

**Module dependency chain:**
```
main → web → service → dao → model
                 ↓      ↑
              common ──→ model
main → common
```

- **finance-tool-model** — Entity classes with MyBatis Plus annotations (`com.finance.model.entity`)
- **finance-tool-dao** — MyBatis Plus mapper interfaces (`com.finance.dao.mapper`). Scanned via `@MapperScan` on the Application class.
- **finance-tool-service** — Business logic. Depends on model, dao, and common. Contains PDF processing via Apache PDFBox 3.0.1.
- **finance-tool-common** — Security (Spring Security + JWT via jjwt 0.12.6), global exception handler, `Result` response wrapper
- **finance-tool-web** — REST controllers
- **finance-tool-main** — Entry point. Aggregates all modules. Application class at `com.finance.main.Application`.

**Key patterns:**
- JWT authentication with access + refresh tokens (configurable expiration in application.yml)
- API responses wrapped in `Result<T>` from common module
- All packages under `com.finance.*`, component scan covers `com.finance` base package
- File uploads capped at 10MB
