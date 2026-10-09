resource "random_password" "session_secret" {
  length  = 48
  special = false
}

resource "aws_iam_role" "backend" {
  name = "splitforce-backend"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "backend" {
  name = "splitforce-backend"
  role = aws_iam_role.backend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.backend.arn}:*"
      },
      {
        # ListTables/DescribeTable: DynamoDbTableInitializer checks the tables exist on startup.
        Effect   = "Allow"
        Action   = ["dynamodb:ListTables"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:DescribeTable", "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem",
          "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:Scan", "dynamodb:BatchGetItem",
          "dynamodb:BatchWriteItem",
        ]
        Resource = flatten([for t in local.dynamodb_tables : [t.arn, "${t.arn}/index/*"]])
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.blobs.arn}/*"
      },
      {
        # HeadObject on a missing key returns 403 instead of 404 without ListBucket.
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.blobs.arn
      },
    ]
  })
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/aws/lambda/splitforce-backend"
  retention_in_days = 7
}

resource "aws_lambda_function" "backend" {
  function_name    = "splitforce-backend"
  role             = aws_iam_role.backend.arn
  runtime          = "java21"
  architectures    = ["arm64"]
  handler          = "com.splitforce.StreamLambdaHandler::handleRequest"
  filename         = var.lambda_zip
  source_code_hash = filebase64sha256(var.lambda_zip)
  memory_size      = 1024
  timeout          = 30
  publish          = true

  # Snapshot the started Spring app per published version: ~1 s cold starts instead of ~8 s.
  # No extra charge for Java runtimes.
  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = {
      GOOGLE_CLIENT_ID     = var.google_client_id
      GOOGLE_CLIENT_SECRET = var.google_client_secret
      PUBLIC_URL           = var.public_url
      FRONTEND_URL         = var.public_url
      SESSION_SECRET       = random_password.session_secret.result
      COOKIE_SECURE        = "true"
      DYNAMODB_ENDPOINT    = ""
      STORAGE_MODE         = "s3"
      AWS_S3_BUCKET        = aws_s3_bucket.blobs.bucket
      SEED_ENABLED         = "false"
      JAVA_TOOL_OPTIONS    = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    }
  }

  depends_on = [aws_cloudwatch_log_group.backend, aws_iam_role_policy.backend]
}

# SnapStart only applies to published versions, so the URL points at an alias, not $LATEST.
resource "aws_lambda_alias" "live" {
  name             = "live"
  function_name    = aws_lambda_function.backend.function_name
  function_version = aws_lambda_function.backend.version
}

# Public URL; CloudFront is the only intended caller. (CloudFront OAC for Lambda would require
# browsers to sign POST bodies, which this app can't do.)
resource "aws_lambda_function_url" "live" {
  function_name      = aws_lambda_function.backend.function_name
  qualifier          = aws_lambda_alias.live.name
  authorization_type = "NONE"
}

resource "aws_lambda_permission" "url" {
  statement_id           = "FunctionUrlPublic"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.backend.function_name
  qualifier              = aws_lambda_alias.live.name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "url_invoke" {
  statement_id             = "FunctionUrlInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.backend.function_name
  qualifier                = aws_lambda_alias.live.name
  principal                = "*"
  invoked_via_function_url = true
}
