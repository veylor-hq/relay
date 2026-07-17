# Veylor Relay

Veylor Relay is a high-performance, multi-tenant, and GDPR-compliant notification routing microservice built using **Spring Boot** and **Java**. 

By decoupling Personally Identifiable Information (PII) from your primary user-facing systems and managing it entirely within Veylor Relay, you shrink the compliance and security surface area of your core applications.

## Key Features

1. **GDPR-Compliant Recipient Sync:** Client applications can synchronize a recipient's contact information once, receive a unique `recipient_id` (UUID), and purge plain-text emails from their systems.
2. **Double-Read Body Caching:** Utilizes a custom `HttpServletRequestWrapper` to read request bodies multiple times (for signature verification and controller parsing).
3. **HMAC-SHA256 Request Verification:** Validates webhook authenticity using signature checks. Only API key hashes are stored in the database for optimal security.
4. **Idempotency Protection:** Enforces unique transaction routing via `X-RELAY-Nonce` headers, preventing double-sends in case of network retries.
5. **Java Virtual Threads Async Processing:** Offloads bulk notification sends to lightweight Virtual Threads, enabling concurrent SMTP delivery without blocking threads.
6. **Tenant Management (Admin CRUD):** Administrative endpoints to register, update, and manage third-party applications.

## Getting Started

### Prerequisites
- **Java 21+** (Java 26 supported)
- **Docker & Docker Compose** (for PostgreSQL database orchestration)
- **Mailpit** (local SMTP test server running on port `1025`)

### Setup Configuration
1. Copy the sample environment template file to `.env` and adjust the variables if needed:
   ```bash
   cp sample.env .env
   ```

2. Start the database services:
   ```bash
   make db-up
   ```

3. Run the application:
   ```bash
   make dev
   ```


## Makefile Shortcut Commands

We use a `Makefile` to simplify development tasks:

| Command | Action |
| :--- | :--- |
| `make dev` | Starts the local Spring Boot application. |
| `make test` | Runs the test suite (uses fast, in-memory H2 database). |
| `make test-clean` | Cleans build caches and runs all tests from scratch. |
| `make build` | Compiles the production jar. |
| `make db-up` | Starts the PostgreSQL database container. |
| `make db-down` | Stops the database and clears the persistent volumes. |
| `make lint` | Runs Checkstyle code quality/formatting checks. |

## Example Client Integrations

We provide pre-built client examples to demonstrate how to integrate your services with Veylor Relay:

- **[FastAPI Demo App](./examples/fastapi_demo)**: A Python demonstration app showing how to authenticate administrative requests, load configurations, compute HMAC signatures, and dispatch bulk newsletters securely.

## System Design  
Coming soon....

## Detailed Walkthrough  
Coming soon....