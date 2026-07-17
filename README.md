# Veylor Relay
Veylor Relay is a high-performance, multi-tenant notification routing proxy and PII isolation vault.
It is designed to serve as a centralized privacy boundary across the Veylor service ecosystem, including the core **eGarage GMS (Garage Management System)** and auxiliary billing, booking, and inventory systems, aiming to reduce the risk profile of data protection compliance.

By decoupling Personally Identifiable Information (PII) from primary client-facing nodes and tokenizing recipient details into an identity-decoupled `recipient_id` (UUID), Veylor Relay aims to significantly reduce the security compliance surface area of the downstream microservice infrastructure.

## Key Features

1. **Recipient Data Minimization & Sync:** Client applications can synchronize a recipient's contact information once, receive a unique `recipient_id` (UUID), allowing them to minimize plain-text emails on their systems.
2. **Double-Read Body Caching:** Utilizes a custom `HttpServletRequestWrapper` to read request bodies multiple times (for signature verification and controller parsing).
3. **HMAC-SHA256 Request Verification:** Validates webhook authenticity using signature checks. Only API key hashes are stored in the database for optimal security.
4. **Idempotency Protection:** Enforces unique transaction routing via `X-RELAY-Nonce` headers, preventing double-sends in case of network retries.
5. **Java Virtual Threads Async Processing:** Offloads bulk notification sends to lightweight Virtual Threads, enabling concurrent SMTP delivery without blocking threads.
6. **Tenant Management (Admin CRUD):** Administrative endpoints to register, update, and manage third-party applications.

## Security Compliance & Operational Assumptions

To achieve its compliance and risk-reduction goals, Veylor Relay relies on the following operational assumptions and controls:
- **Retention & Deletion:** Downstream clients must ensure they purge local plain-text copies of PII once tokenized. The Relay database must enforce appropriate retention policies on the tokenized data.
- **Access Control:** The admin API and application API keys must be strictly managed; compromised keys could expose mappings.
- **Logging:** System logs must be configured to prevent the logging of payload contents and sensitive data in plain-text.
- **Tenant Isolation:** Multi-tenancy isolation is enforced at the software layer via API key checking; underlying network and database resource sharing requires standard containerization and deployment boundaries.

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