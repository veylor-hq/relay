import hmac
import hashlib
import uuid
import httpx
import json
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from typing import List

# Load environment configuration
load_dotenv()

app = FastAPI(title="Kaiser Admin Portal")

# Mock database recipients
RECIPIENTS = [
    "john.doe@example.com",
    "alice.smith@example.com",
    "bob.jones@example.com"
]

RELAY_URL = os.getenv("RELAY_URL", "http://localhost:8080")
RELAY_API_KEY = os.getenv("RELAY_API_KEY")

security = HTTPBearer()

def get_current_admin(credentials: HTTPAuthorizationCredentials = Depends(security)):
    token = credentials.credentials
    if token != "secret_admin_token":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid administration token"
        )
    return "admin"

class NewsletterPayload(BaseModel):
    subject: str
    content: str

def sign_payload(body: bytes, api_key: str, nonce: str, path: str) -> str:
    # Build canonical request string: METHOD\nPATH\nNONCE\nBODY
    canonical = f"POST\n{path}\n{nonce}\n".encode('utf-8') + body
    return hmac.new(
        api_key.encode('utf-8'),
        canonical,
        hashlib.sha256
    ).hexdigest()

@app.post("/admin/newsletter")
async def send_newsletter(payload: NewsletterPayload, admin: str = Depends(get_current_admin)):
    """
    Simulates sending a newsletter to our user database.
    Calculates the required HMAC-SHA256 signature and delegates bulk delivery to Veylor Relay.
    """
    notifications = []
    for email in RECIPIENTS:
        notifications.append({
            "email": email,
            "subject": payload.subject,
            "content": payload.content,
            "type": "EMAIL",
            "level": "INFO"
        })

    bulk_request = {"notifications": notifications}
    body_bytes = json.dumps(bulk_request, separators=(',', ':')).encode('utf-8')
    
    nonce = str(uuid.uuid4())
    path = "/api/v1/notifications/bulk"
    
    # Generate the security signature for the request body
    signature = sign_payload(body_bytes, RELAY_API_KEY, nonce, path)

    headers = {
        "X-RELAY-API-Key": RELAY_API_KEY,
        "X-RELAY-Authorization": signature,
        "X-RELAY-Nonce": nonce,
        "Content-Type": "application/json"
    }

    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(
                f"{RELAY_URL}{path}",
                content=body_bytes,
                headers=headers
            )
            if response.status_code != 202:
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail=f"Relay returned error: {response.text}"
                )
            return response.json()
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Failed to connect to Veylor Relay: {exc}"
            )

class OtpRequest(BaseModel):
    email: str
    code: str

@app.post("/auth/otp")
async def send_otp(payload: OtpRequest):
    """
    Sends a security OTP auth code email to the user.
    Uses the single notification endpoint on Veylor Relay.
    """
    item = {
        "email": payload.email,
        "subject": "Your One-Time Security Code",
        "content": f"Your authentication code is: {payload.code}. It is valid for 5 minutes.",
        "type": "EMAIL",
        "level": "SECURITY"
    }

    body_bytes = json.dumps(item, separators=(',', ':')).encode('utf-8')
    nonce = str(uuid.uuid4())
    path = "/api/v1/notifications/single"
    
    signature = sign_payload(body_bytes, RELAY_API_KEY, nonce, path)

    headers = {
        "X-RELAY-API-Key": RELAY_API_KEY,
        "X-RELAY-Authorization": signature,
        "X-RELAY-Nonce": nonce,
        "Content-Type": "application/json"
    }

    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(
                f"{RELAY_URL}{path}",
                content=body_bytes,
                headers=headers
            )
            if response.status_code != 200:
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail=f"Relay returned error: {response.text}"
                )
            return response.json()
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Failed to connect to Veylor Relay: {exc}"
            )
