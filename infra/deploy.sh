#!/usr/bin/env bash
# Builds backend + frontend and deploys everything. Usage: infra/deploy.sh [--plan]
set -euo pipefail
cd "$(dirname "$0")"
ROOT=..
export AWS_PROFILE="${AWS_PROFILE:-splitforce}"

# Google OAuth credentials come from backend/.env.local (never echoed).
TF_VAR_google_client_id=$(grep -E '^GOOGLE_CLIENT_ID=' $ROOT/backend/.env.local | cut -d= -f2-)
TF_VAR_google_client_secret=$(grep -E '^GOOGLE_CLIENT_SECRET=' $ROOT/backend/.env.local | cut -d= -f2-)
export TF_VAR_google_client_id TF_VAR_google_client_secret

echo "==> Building backend"
(cd $ROOT/backend && mvn -q -DskipTests clean package)

terraform init -input=false >/dev/null

# The Lambda needs the CloudFront URL and CloudFront needs the Lambda URL, so the URL is passed
# in from the previous apply's output (empty on the very first run, which then applies twice).
current_url() { terraform output -raw public_url 2>/dev/null || true; }
URL=$(current_url)

if [ "${1:-}" = "--plan" ]; then
  terraform plan -input=false -var "public_url=$URL"
  exit 0
fi

echo "==> Applying infrastructure"
terraform apply -input=false -auto-approve -var "public_url=$URL"
if [ "$(current_url)" != "$URL" ]; then
  URL=$(current_url)
  echo "==> Second pass: pointing the backend at $URL"
  terraform apply -input=false -auto-approve -var "public_url=$URL"
fi

echo "==> Waiting for the SnapStart snapshot of the new version"
VERSION=$(aws lambda get-alias --function-name splitforce-backend --name live --query FunctionVersion --output text)
aws lambda wait function-active-v2 --function-name splitforce-backend --qualifier "$VERSION"

echo "==> Building and uploading frontend"
(cd $ROOT/frontend && npm ci --silent && npm run build --silent)
WEB=$(terraform output -raw web_bucket)
# Hashed assets never change; index.html must always be revalidated.
aws s3 sync $ROOT/frontend/dist/assets "s3://$WEB/assets" --delete --cache-control "public,max-age=31536000,immutable"
aws s3 sync $ROOT/frontend/dist "s3://$WEB" --delete --exclude "assets/*" --cache-control "no-cache"
aws cloudfront create-invalidation --distribution-id "$(terraform output -raw cloudfront_distribution_id)" --paths "/*" >/dev/null

echo
echo "Deployed: $URL"
echo "Google OAuth redirect URI (must be registered): $(terraform output -raw google_redirect_uri)"
