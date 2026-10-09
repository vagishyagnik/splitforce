# Emails as soon as the account is charged anything. AWS Budgets itself is free (first 2 budgets).
resource "aws_budgets_budget" "zero_spend" {
  count        = var.budget_alert_email == "" ? 0 : 1
  name         = "splitforce-zero-spend"
  budget_type  = "COST"
  limit_amount = "1.0"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 1
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_alert_email]
  }
}
