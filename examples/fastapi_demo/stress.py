import asyncio
import time
import random
import string
import httpx

# Configuration
URL = "http://localhost:8000/auth/otp"

# Base traffic profile: (duration_seconds, base_rps)
TRAFFIC_PROFILE = [
    (30, 10),      # Baseline
    (15, 25),      # Ramp-up step 1
    (15, 50),      # Ramp-up step 2
    (20, 80),      # Approaching peak
    (10, 150),     # Surge onset
    (20, 250),     # Peak burst
    (15, 180),     # Post-peak sustained load
    (20, 60),      # Graceful taper
    (30, 20),      # Cooldown
    (30, 5),       # Idle tail
]

def generate_random_payload() -> dict:
    """Generates a randomized payload to prevent caching or duplicate filtering."""
    # Generate random 8-character string for unique user emails
    user_id = "".join(random.choices(string.ascii_lowercase + string.digits, k=8))
    # Generate random 6-digit code
    code = f"{random.randint(0, 999999):06d}"
    
    return {
        "email": f"testuser_{user_id}@example.com",
        "code": code
    }

async def send_request(client: httpx.AsyncClient, results: list):
    """Sends a single POST request with a randomized payload and records metrics."""
    payload = generate_random_payload()
    start_time = time.perf_counter()
    try:
        response = await client.post(URL, json=payload, timeout=15.0)
        latency = (time.perf_counter() - start_time) * 1000  # ms
        results.append((response.status_code, latency, None))
    except Exception as e:
        latency = (time.perf_counter() - start_time) * 1000
        results.append((None, latency, str(e)))

def apply_rps_jitter(target_rps: int, jitter_percent: float = 0.15) -> int:
    """
    Applies Gaussian noise to the target RPS to simulate real-world organic variance.
    Prevents artificially rigid step-function load patterns.
    """
    if target_rps <= 0:
        return 0
    # Use normal distribution centered on target_rps with standard deviation based on jitter percentage
    std_dev = target_rps * jitter_percent
    jittered_rps = int(random.gauss(target_rps, std_dev))
    return max(1, jittered_rps)

async def main():
    results = []
    
    print("--- Starting Spike Load Test with Jitter & Dynamic Payloads ---")
    print(f"Target URL: {URL}\n")

    start_total_time = time.perf_counter()

    # Configure client pool limits to accommodate high spike concurrency
    limits = httpx.Limits(max_connections=350, max_keepalive_connections=150)
    async with httpx.AsyncClient(limits=limits) as client:
        active_tasks = []
        
        for stage_idx, (duration, base_rps) in enumerate(TRAFFIC_PROFILE):
            print(f"Stage {stage_idx + 1}: Running for {duration}s (~{base_rps} req/s base)...")
            
            for sec in range(duration):
                sec_start = time.perf_counter()
                
                # Apply organic variance (±15%) to target RPS for this specific second
                current_rps = apply_rps_jitter(base_rps, jitter_percent=0.15)
                
                # Spawn tasks for this second
                for _ in range(current_rps):
                    task = asyncio.create_task(send_request(client, results))
                    active_tasks.append(task)
                
                # Regulate rate: sleep for remaining window
                elapsed = time.perf_counter() - sec_start
                sleep_time = max(0.0, 1.0 - elapsed)
                await asyncio.sleep(sleep_time)

        # Wait for all outstanding requests to complete
        if active_tasks:
            print("\nDraining active connection pool...")
            await asyncio.gather(*active_tasks, return_exceptions=True)

    total_duration = time.perf_counter() - start_total_time

    # Metric aggregation
    status_codes = {}
    errors = {}
    latencies = []

    for status, latency, err in results:
        latencies.append(latency)
        if err:
            err_msg = err.split("(")[0].strip() if "(" in err else err
            errors[err_msg] = errors.get(err_msg, 0) + 1
        else:
            status_codes[status] = status_codes.get(status, 0) + 1

    latencies.sort()
    avg_latency = sum(latencies) / len(latencies) if latencies else 0
    p95_latency = latencies[int(len(latencies) * 0.95)] if latencies else 0
    p99_latency = latencies[int(len(latencies) * 0.99)] if latencies else 0

    print("\n--- Benchmark Results ---")
    print(f"Total Time Elapsed:   {total_duration:.2f} seconds")
    print(f"Total Requests Sent:  {len(results)}")
    print(f"Achieved Throughput:  {len(results) / total_duration:.2f} req/s")
    print(f"Average Latency:      {avg_latency:.2f} ms")
    print(f"95th Percentile:      {p95_latency:.2f} ms")
    print(f"99th Percentile:      {p99_latency:.2f} ms")
    
    print("\nStatus Codes Breakdown:")
    for code, count in sorted(status_codes.items(), key=lambda x: x[0] if x[0] else 0):
        print(f"  HTTP {code}: {count}")
    
    if errors:
        print("\nConnection / Execution Errors:")
        for err_msg, count in errors.items():
            print(f"  {err_msg}: {count}")

if __name__ == "__main__":
    asyncio.run(main())