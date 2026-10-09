variable "region" {
  type    = string
  default = "us-west-2"
}

variable "aws_profile" {
  type    = string
  default = "splitforce"
}

variable "google_client_id" {
  type      = string
  sensitive = true
}

variable "google_client_secret" {
  type      = string
  sensitive = true
}

# https://<cloudfront-domain>. Empty on the very first apply (the distribution doesn't exist yet);
# deploy.sh fills it in from the `public_url` output and applies again.
variable "public_url" {
  type    = string
  default = ""
}

variable "lambda_zip" {
  type    = string
  default = "../backend/target/splitforce-lambda.zip"
}

# Optional: email for a $1/month AWS Budgets alert. Needs budgets:* permissions on the deploy user.
variable "budget_alert_email" {
  type    = string
  default = ""
}
