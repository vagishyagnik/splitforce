#!/usr/bin/env bash
# One-off: loads the seed groups/expenses/pictures into the real AWS tables by running the
# backend locally against AWS (no Lambda time limit, and the Lambda itself never seeds).
set -euo pipefail
cd "$(dirname "$0")"
ROOT=..
export AWS_PROFILE="${AWS_PROFILE:-splitforce}"
export AWS_REGION="$(terraform output -raw blob_bucket >/dev/null && aws configure get region || echo us-west-2)"

(cd $ROOT/backend && mvn -q -DskipTests clean package)

LOG=$(mktemp)
(
  cd $ROOT/backend
  GOOGLE_CLIENT_ID=unused GOOGLE_CLIENT_SECRET=unused \
  DYNAMODB_ENDPOINT= STORAGE_MODE=s3 AWS_S3_BUCKET="$(cd ../infra && terraform output -raw blob_bucket)" \
  SEED_ENABLED=true SERVER_PORT=9191 \
  java -jar target/splitforce-backend-0.0.1-SNAPSHOT.jar
) >"$LOG" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT

# Startup runners (seed + backfills) finish before the health endpoint answers.
for _ in $(seq 1 600); do
  curl -sf localhost:9191/api/health >/dev/null && break
  kill -0 $PID 2>/dev/null || { cat "$LOG"; exit 1; }
  sleep 1
done
grep -E "Seeded|already seeded|backfill|ERROR" "$LOG" || true
echo "Seed complete."
