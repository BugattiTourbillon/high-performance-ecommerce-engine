#!/usr/bin/env python3
import concurrent.futures
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
USERS = int(os.environ.get("USERS", "120"))
STOCK = int(os.environ.get("STOCK", "40"))
REPORT_DIR = Path(os.environ.get("REPORT_DIR", "docs/reports"))
REPORT_DIR.mkdir(parents=True, exist_ok=True)


def request(method: str, path: str, payload: dict | None = None, token: str | None = None) -> tuple[int, dict | str]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE_URL}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        try:
            parsed = json.loads(body) if body else {}
        except json.JSONDecodeError:
            parsed = body
        return exc.code, parsed


def token_from(response: tuple[int, dict | str]) -> str:
    status, body = response
    if status not in (200, 201) or not isinstance(body, dict):
        raise RuntimeError(f"Authentication failed: {status} {body}")
    return str(body["accessToken"])


def setup() -> tuple[int, list[str]]:
    suffix = str(int(time.time()))
    admin_token = token_from(request("POST", "/api/auth/login", {"username": "admin", "password": "Admin1234!"}))
    status, product = request(
        "POST",
        "/api/admin/products",
        {
            "sku": f"STRESS-{suffix}",
            "name": "Checkout Stress Product",
            "description": "Limited inventory product for contention testing",
            "price": 11.00,
            "availableQuantity": STOCK,
        },
        admin_token,
    )
    if status != 201 or not isinstance(product, dict):
        raise RuntimeError(f"Product setup failed: {status} {product}")
    product_id = int(product["id"])

    tokens: list[str] = []
    for index in range(USERS):
        username = f"stress{suffix}{index}"
        token = token_from(
            request(
                "POST",
                "/api/auth/register",
                {"username": username, "email": f"{username}@example.com", "password": "Secret123!"},
            )
        )
        add_status, add_body = request("POST", "/api/cart/items", {"productId": product_id, "quantity": 1}, token)
        if add_status != 200:
            raise RuntimeError(f"Cart setup failed for {username}: {add_status} {add_body}")
        tokens.append(token)
    return product_id, tokens


def checkout(token: str) -> tuple[int, float]:
    started = time.perf_counter()
    status, _ = request("POST", "/api/orders/checkout", {"paymentToken": f"tok_stress_{time.time_ns()}"}, token)
    elapsed_ms = (time.perf_counter() - started) * 1000
    return status, elapsed_ms


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1)))))
    return ordered[index] if ordered else 0.0


def main() -> None:
    product_id, tokens = setup()
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=USERS) as executor:
        results = list(executor.map(checkout, tokens))
    wall_seconds = time.perf_counter() - started

    statuses: dict[int, int] = {}
    latencies = []
    for status, elapsed in results:
        statuses[status] = statuses.get(status, 0) + 1
        latencies.append(elapsed)
    success = statuses.get(200, 0)
    clean_rejections = statuses.get(400, 0)
    server_errors = sum(count for status, count in statuses.items() if status >= 500 or status == 0)

    status, product = request("GET", f"/api/products/{product_id}")
    remaining = product.get("availableQuantity", "unknown") if status == 200 and isinstance(product, dict) else "unknown"

    report = [
        "# Checkout Contention Stress Report",
        "",
        f"- Base URL: `{BASE_URL}`",
        f"- Concurrent users: `{USERS}`",
        f"- Initial stock: `{STOCK}`",
        f"- Product ID: `{product_id}`",
        f"- Wall time: `{wall_seconds:.2f}s`",
        f"- Generated: `{time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}`",
        "",
        "| Metric | Value |",
        "| --- | ---: |",
        f"| Successful checkouts | {success} |",
        f"| Clean insufficient-stock/payment rejections | {clean_rejections} |",
        f"| Server errors | {server_errors} |",
        f"| Remaining inventory | {remaining} |",
        f"| p50 checkout latency | {percentile(latencies, 50):.2f} ms |",
        f"| p95 checkout latency | {percentile(latencies, 95):.2f} ms |",
        f"| Max checkout latency | {max(latencies):.2f} ms |",
        "",
        "Expected integrity result: successful checkouts must never exceed initial stock, remaining inventory must never be negative, and server errors must be zero.",
        "",
        f"Observed HTTP statuses: `{statuses}`",
    ]
    path = REPORT_DIR / "checkout-contention-report.md"
    path.write_text("\n".join(report) + "\n", encoding="utf-8")
    print(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
