output "public_url" {
  value = "https://${aws_cloudfront_distribution.site.domain_name}"
}

output "google_redirect_uri" {
  value = "https://${aws_cloudfront_distribution.site.domain_name}/login/oauth2/code/google"
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.site.id
}

output "web_bucket" {
  value = aws_s3_bucket.web.bucket
}

output "blob_bucket" {
  value = aws_s3_bucket.blobs.bucket
}

output "lambda_function_url" {
  value = aws_lambda_function_url.live.function_url
}
