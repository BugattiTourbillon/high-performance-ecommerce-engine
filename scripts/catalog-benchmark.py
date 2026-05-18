#!/usr/bin/env python3
import concurrent.futures
import csv
import json
import math
import statistics
import time
import urllib.request
from pathlib import Path
import os

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
REQUESTS = int(os.environ.get("REQUESTS", "240"))
CONCURRENCY = int(os.environ.get("CONCURRENCY", "40"))
REPORT_DIR = Path(os.environ.get("REPORT_DIR", "docs/reports"))
SCREENSHOT_DIR = REPORT_DIR / "screenshots"
REPORT_DIR.mkdir(parents=True, exist_ok=True)
SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

SCENARIOS = [
    ("before_raw_catalog", f"{BASE_URL}/api/benchmark/products/raw?page=0&size=20"),
    ("after_cached_catalog", f"{BASE_URL}/api/products?page=0&size=20"),
    ("before_raw_popular", f"{BASE_URL}/api/benchmark/products/popular/raw"),
    ("after_cached_popular", f"{BASE_URL}/api/products/popular"),
]


def fetch(url: str) -> tuple[int, float]:
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=15) as response:
            response.read()
            status = response.status
    except Exception:
        status = 0
    elapsed_ms = (time.perf_counter() - started) * 1000
    return status, elapsed_ms


def percentile(values: list[float], p: float) -> float:
    if not values:
        return math.nan
    ordered = sorted(values)
    index = int(math.ceil((p / 100) * len(ordered))) - 1
    return ordered[max(0, min(index, len(ordered) - 1))]


def run_scenario(name: str, url: str) -> dict:
    for _ in range(10):
        fetch(url)

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
        results = list(executor.map(lambda _: fetch(url), range(REQUESTS)))
    wall_seconds = time.perf_counter() - started

    latencies = [elapsed for status, elapsed in results if status == 200]
    failures = [status for status, _ in results if status != 200]
    return {
        "scenario": name,
        "url": url,
        "requests": REQUESTS,
        "concurrency": CONCURRENCY,
        "success": len(latencies),
        "failures": len(failures),
        "error_rate_pct": round((len(failures) / REQUESTS) * 100, 2),
        "throughput_rps": round(REQUESTS / wall_seconds, 2),
        "avg_ms": round(statistics.mean(latencies), 2) if latencies else math.nan,
        "p50_ms": round(percentile(latencies, 50), 2),
        "p95_ms": round(percentile(latencies, 95), 2),
        "max_ms": round(max(latencies), 2) if latencies else math.nan,
    }


def write_svg(rows: list[dict]) -> None:
    labels = [row["scenario"] for row in rows]
    values = [float(row["p95_ms"]) for row in rows]
    max_value = max(values) if values else 1
    width = 920
    height = 360
    left = 210
    bar_height = 36
    gap = 28
    chart_width = 620
    svg_rows = []
    for index, (label, value) in enumerate(zip(labels, values)):
        y = 58 + index * (bar_height + gap)
        bar_width = 1 if max_value == 0 else (value / max_value) * chart_width
        color = "#b45309" if label.startswith("before") else "#047857"
        svg_rows.append(f'<text x="24" y="{y + 24}" font-size="15" fill="#111827">{label}</text>')
        svg_rows.append(f'<rect x="{left}" y="{y}" width="{bar_width:.1f}" height="{bar_height}" rx="4" fill="{color}"/>')
        svg_rows.append(f'<text x="{left + bar_width + 12:.1f}" y="{y + 24}" font-size="15" fill="#111827">{value:.2f} ms</text>')
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <rect width="100%" height="100%" fill="#ffffff"/>
  <text x="24" y="32" font-size="22" font-family="Arial" font-weight="700" fill="#111827">Before vs After Catalog Benchmark - p95 latency</text>
  <g font-family="Arial">
    {''.join(svg_rows)}
  </g>
</svg>
'''
    (SCREENSHOT_DIR / "catalog-before-after-p95.svg").write_text(svg, encoding="utf-8")


def main() -> None:
    rows = [run_scenario(name, url) for name, url in SCENARIOS]
    csv_path = REPORT_DIR / "catalog-benchmark.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    write_svg(rows)

    md = [
        "# Catalog Benchmark Report",
        "",
        f"- Base URL: `{BASE_URL}`",
        f"- Requests per scenario: `{REQUESTS}`",
        f"- Concurrency: `{CONCURRENCY}`",
        f"- Generated: `{time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}`",
        "",
        "| Scenario | Success | Failures | Error rate | Throughput | Avg | p50 | p95 | Max |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in rows:
        md.append(
            f"| {row['scenario']} | {row['success']} | {row['failures']} | {row['error_rate_pct']}% | "
            f"{row['throughput_rps']} rps | {row['avg_ms']} ms | {row['p50_ms']} ms | {row['p95_ms']} ms | {row['max_ms']} ms |"
        )
    md.extend([
        "",
        "Interpretation: the `before_*` endpoints bypass Redis and intentionally represent the unoptimized read path. "
        "The `after_*` endpoints are the production optimized cacheable APIs.",
        "",
        "Chart artifact: `docs/reports/screenshots/catalog-before-after-p95.svg`",
    ])
    report_path = REPORT_DIR / "catalog-benchmark.md"
    report_path.write_text("\n".join(md) + "\n", encoding="utf-8")
    print(report_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
