#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REPORT_DIR="${REPORT_DIR:-docs/reports}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/api-smoke-test.md"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

pass_count=0
fail_count=0

json_value() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

path = sys.argv[2].split(".")
with open(sys.argv[1], "r", encoding="utf-8") as handle:
    value = json.load(handle)
for part in path:
    if isinstance(value, list):
        value = value[int(part)]
    else:
        value = value[part]
print(value)
PY
}

request() {
  local name="$1"
  local expected="$2"
  shift 2
  local body="$TMP_DIR/${name//[^A-Za-z0-9]/_}.json"
  local status
  status="$(curl -sS -o "$body" -w "%{http_code}" "$@")"
  if [[ "$status" == "$expected" ]]; then
    printf '| %s | `%s` | pass |\n' "$name" "$status" >> "$REPORT_FILE"
    pass_count=$((pass_count + 1))
  else
    printf '| %s | `%s`, expected `%s` | fail |\n' "$name" "$status" "$expected" >> "$REPORT_FILE"
    printf 'Smoke test failed: %s returned %s, expected %s\n' "$name" "$status" "$expected" >&2
    cat "$body" >&2 || true
    fail_count=$((fail_count + 1))
    return 1
  fi
  printf '%s' "$body"
}

suffix="$(date +%s)"
today="$(date -u +%F)"

cat > "$REPORT_FILE" <<EOF_REPORT
# API Smoke Test Report

- Base URL: \`$BASE_URL\`
- Run time: \`$(date -u +"%Y-%m-%dT%H:%M:%SZ")\`

| Check | HTTP result | Status |
| --- | --- | --- |
EOF_REPORT

health_body="$(request "health" "200" "$BASE_URL/actuator/health")"
products_body="$(request "public product list" "200" "$BASE_URL/api/products?page=0&size=20")"
request "public popular products" "200" "$BASE_URL/api/products/popular" >/dev/null
request "public raw baseline products" "200" "$BASE_URL/api/benchmark/products/raw?page=0&size=20" >/dev/null

admin_login_body="$(request "admin login" "200" \
  -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin1234!"}')"
admin_token="$(json_value "$admin_login_body" accessToken)"

register_body="$(request "customer register" "201" \
  -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"buyer$suffix\",\"email\":\"buyer$suffix@example.com\",\"password\":\"Secret123!\"}")"
customer_token="$(json_value "$register_body" accessToken)"

created_product_body="$(request "admin create product" "201" \
  -X POST "$BASE_URL/api/admin/products" \
  -H "Authorization: Bearer $admin_token" \
  -H "Content-Type: application/json" \
  -d "{\"sku\":\"SMOKE-$suffix\",\"name\":\"Smoke Test Product\",\"description\":\"Created by smoke test\",\"price\":25.50,\"availableQuantity\":20}")"
product_id="$(json_value "$created_product_body" id)"

request "product details" "200" "$BASE_URL/api/products/$product_id" >/dev/null
request "raw product details" "200" "$BASE_URL/api/benchmark/products/$product_id/raw" >/dev/null
request "admin update inventory" "200" \
  -X PUT "$BASE_URL/api/admin/inventory/$product_id" \
  -H "Authorization: Bearer $admin_token" \
  -H "Content-Type: application/json" \
  -d '{"availableQuantity":20}' >/dev/null

request "customer get cart" "200" \
  -H "Authorization: Bearer $customer_token" \
  "$BASE_URL/api/cart" >/dev/null
request "customer add cart item" "200" \
  -X POST "$BASE_URL/api/cart/items" \
  -H "Authorization: Bearer $customer_token" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":$product_id,\"quantity\":2}" >/dev/null
request "customer update cart item" "200" \
  -X PUT "$BASE_URL/api/cart/items/$product_id" \
  -H "Authorization: Bearer $customer_token" \
  -H "Content-Type: application/json" \
  -d '{"quantity":1}' >/dev/null
request "customer checkout" "200" \
  -X POST "$BASE_URL/api/orders/checkout" \
  -H "Authorization: Bearer $customer_token" \
  -H "Content-Type: application/json" \
  -d '{"paymentToken":"tok_smoke_success"}' >/dev/null
request "customer list orders" "200" \
  -H "Authorization: Bearer $customer_token" \
  "$BASE_URL/api/orders" >/dev/null

request "admin daily sales job" "200" \
  -X POST "$BASE_URL/api/admin/jobs/daily-sales?salesDate=$today" \
  -H "Authorization: Bearer $admin_token" >/dev/null
request "admin daily sales report" "200" \
  -H "Authorization: Bearer $admin_token" \
  "$BASE_URL/api/admin/reports/daily-sales/$today" >/dev/null

request "unauthorized cart rejection" "401" "$BASE_URL/api/cart" >/dev/null
request "customer admin rejection" "403" \
  -X POST "$BASE_URL/api/admin/products" \
  -H "Authorization: Bearer $customer_token" \
  -H "Content-Type: application/json" \
  -d "{\"sku\":\"DENIED-$suffix\",\"name\":\"Denied\",\"description\":\"Denied\",\"price\":1,\"availableQuantity\":1}" >/dev/null

cat >> "$REPORT_FILE" <<EOF_REPORT

Summary: $pass_count passed, $fail_count failed.

Expected result: all rows should be \`pass\`; invoices should appear under \`generated/invoices/\` after checkout.
EOF_REPORT

cat "$REPORT_FILE"
