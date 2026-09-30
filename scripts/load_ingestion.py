#!/usr/bin/env python3
"""Small local-only ingestion measurement; not a production benchmark."""

import argparse
import concurrent.futures
import statistics
import time
import urllib.error
import urllib.request


def request(url, body):
    started = time.perf_counter()
    try:
        request = urllib.request.Request(url, data=body, method="POST")
        request.add_header("Content-Type", "application/octet-stream")
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status == 204, (time.perf_counter() - started) * 1000
    except (urllib.error.URLError, TimeoutError, ValueError):
        return False, (time.perf_counter() - started) * 1000


def percentile(values, percent):
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, round((len(ordered) - 1) * percent)))]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="Public /hooks/{publicKey} URL")
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--body", default="hookscope-load-test")
    args = parser.parse_args()
    if args.requests < 1 or args.concurrency < 1:
        parser.error("--requests and --concurrency must both be at least 1")

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results = list(executor.map(lambda _: request(args.url, args.body.encode()), range(args.requests)))
    elapsed = time.perf_counter() - started
    successes = sum(success for success, _ in results)
    latencies = [latency for _, latency in results]
    print("local development-machine measurement, not a production benchmark")
    print(f"total_requests={args.requests}")
    print(f"concurrency={args.concurrency}")
    print(f"elapsed_seconds={elapsed:.3f}")
    print(f"requests_per_second={args.requests / elapsed:.2f}")
    print(f"successful={successes}")
    print(f"failed={args.requests - successes}")
    print(f"p50_ms={statistics.median(latencies):.2f}")
    print(f"p95_ms={percentile(latencies, 0.95):.2f}")


if __name__ == "__main__":
    main()
