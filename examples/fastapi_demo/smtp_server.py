import asyncio
import time
from collections import deque
from aiosmtpd.controller import Controller

# --- CONFIGURATION ---
MAX_RPM = 300              # Maximum allowed emails per 60 seconds
WINDOW_SECONDS = 60.0      # Sliding window time frame in seconds
LATENCY_MS = 50            # Simulated network latency (ms) per request


class SlidingWindowRateLimiterSMTPHandler:
    def __init__(self):
        # Stores timestamps of accepted messages in the current window
        self.timestamps = deque()
        self.total_accepted = 0
        self.total_rejected = 0

    async def handle_DATA(self, server, session, envelope):
        now = time.time()

        # 1. Purge timestamps older than 60 seconds
        while self.timestamps and (now - self.timestamps[0]) > WINDOW_SECONDS:
            self.timestamps.popleft()

        # 2. Simulate standard network/TLS latency (50ms)
        await asyncio.sleep(LATENCY_MS / 1000.0)

        # 3. Check rate limit
        current_rpm = len(self.timestamps)

        if current_rpm >= MAX_RPM:
            self.total_rejected += 1
            print(f"❌ [421 RATE LIMITED] Rejected request | Current RPM: {current_rpm}/{MAX_RPM} in last 60s | Total Rejected: {self.total_rejected}")
            return "421 4.7.0 Rate limit exceeded (300 msgs/min limit). Try again later."

        # 4. Accept message & log timestamp
        self.timestamps.append(now)
        self.total_accepted += 1
        print(f"✅ [250 OK] Accepted email #{self.total_accepted} | Current RPM: {len(self.timestamps)}/{MAX_RPM}")
        return "250 2.0.0 OK Message accepted for delivery"


if __name__ == "__main__":
    handler = SlidingWindowRateLimiterSMTPHandler()
    controller = Controller(handler, hostname="127.0.0.1", port=1025)

    print("=========================================================")
    print("🚀 Mock SMTP Server Running on 127.0.0.1:1025")
    print(f"   - Strict Rate Limit: {MAX_RPM} requests per {WINDOW_SECONDS} seconds")
    print("=========================================================\n")

    controller.start()

    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        loop.run_forever()
    except KeyboardInterrupt:
        print("\nStopping Mock SMTP Server...")
        controller.stop()