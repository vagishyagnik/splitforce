# Run SplitForce locally

Three terminals. In order.

## 1. DynamoDB

```
docker compose up -d
```

Runs local DynamoDB on `:8000`. Data is in-memory — stopping the container wipes it.

## 2. Backend (`:9090`)

```
cd backend
cp .env.local.example .env.local   # first time only, then fill in Google OAuth
./run.sh
```

`.env.local` needs `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. To store profile pics / receipts in S3, set `STORAGE_MODE=s3` and fill in `AWS_S3_*` — leave `STORAGE_MODE=local` to use `./data`.

## 3. Frontend (`:7173`)

```
cd frontend
npm install   # first time only
npm run dev
```

Open http://localhost:7173.

## Ports

- 8000 — DynamoDB
- 9090 — Backend
- 7173 — Frontend
