#!/usr/bin/env bash
# Sends a correctly signed payment_intent.succeeded event to a locally running app.
# Usage: scripts/send-test-webhook.sh [payment_intent_id] [amount_cents] [fee_cents]
set -euo pipefail

SECRET="${STRIPE_WEBHOOK_SECRET:-whsec_local_development_only}"
URL="${URL:-http://localhost:8080/webhooks/stripe}"
INTENT="${1:-pi_demo_$RANDOM}"
AMOUNT="${2:-10000}"
FEE="${3:-320}"

PAYLOAD=$(printf '{"id":"evt_%s","type":"payment_intent.succeeded","data":{"object":{"id":"%s","amount":%s,"amount_received":%s,"currency":"eur","latest_charge":{"balance_transaction":{"fee":%s}}}}}' \
  "$RANDOM$RANDOM" "$INTENT" "$AMOUNT" "$AMOUNT" "$FEE")
T=$(date +%s)
SIG=$(printf '%s.%s' "$T" "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.*= //')

curl -sS -X POST "$URL" \
  -H 'Content-Type: application/json' \
  -H "Stripe-Signature: t=$T,v1=$SIG" \
  -d "$PAYLOAD"
echo
