# FastAPI Client Demo

This is an example client integration demonstrating how a Python backend (like FastAPI or Flask) interacts with Veylor Relay securely.

It showcases:
1. **Request Body signing:** Using `hmac` and `hashlib.sha256` to construct and sign webhook payload packages.
2. **Bulk dispatch:** Constructing notification arrays and executing async tasks.

---

## Setup & Running

1. **Install Poetry** (if not installed):
   ```bash
   pip install poetry
   ```

2. **Install dependencies:**
   ```bash
   poetry install
   ```

3. **Register the application with Veylor Relay:**
   Make sure the Java Relay application is running (`./gradlew bootRun`), then run the setup script:
   ```bash
   bash register_app.sh
   ```
   This script loads the admin token from your root `.env` file, registers a new client application with Veylor Relay, and creates your local configuration file `examples/fastapi_demo/.env` with the generated API key.

4. **Start the API server:**
   ```bash
   poetry run uvicorn main:app --reload --port 8000
   ```

---

## Triggering a Newsletter Dispatch (Bulk Notifications)

Run this curl command to trigger a bulk newsletter delivery:

```bash
curl -X POST http://localhost:8000/admin/newsletter \
  -H "Authorization: Bearer secret_admin_token" \
  -H "Content-Type: application/json" \
  -d '{"subject": "Weekly Newsletter", "content": "Here is our weekly news..."}'
```

---

## Triggering a Single "Auth"-like Email (Magic Link / OTP)

Run this curl command to send a single security OTP authentication code:

```bash
curl -X POST http://localhost:8000/auth/otp \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "code": "938104"}'
```
