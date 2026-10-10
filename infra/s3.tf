locals {
  account_id = data.aws_caller_identity.current.account_id
}

# Profile pictures + receipts. Private; only the Lambda reads/writes it.
resource "aws_s3_bucket" "blobs" {
  bucket = "splitforce-blobs-${local.account_id}"
}

# Versioning so an overwrite or delete (a bug, or a leaked key) can be undone; old versions expire.
resource "aws_s3_bucket_versioning" "blobs" {
  bucket = aws_s3_bucket.blobs.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "blobs" {
  bucket     = aws_s3_bucket.blobs.id
  depends_on = [aws_s3_bucket_versioning.blobs]

  rule {
    id     = "expire-old-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

resource "aws_s3_bucket_policy" "blobs" {
  bucket     = aws_s3_bucket.blobs.id
  depends_on = [aws_s3_bucket_public_access_block.blobs]
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      # Refuses any plain-HTTP access.
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.blobs.arn, "${aws_s3_bucket.blobs.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_s3_bucket_public_access_block" "blobs" {
  bucket                  = aws_s3_bucket.blobs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Built frontend (frontend/dist). Private; served only through CloudFront.
resource "aws_s3_bucket" "web" {
  bucket = "splitforce-web-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "CloudFrontRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.web.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.site.arn } }
      }, {
      # Refuses any plain-HTTP access.
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.web.arn, "${aws_s3_bucket.web.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}
