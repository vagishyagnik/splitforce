# Provisioned capacity totals exactly 25 RCU + 25 WCU across tables and GSIs: the always-free
# DynamoDB allowance. Do not raise these without checking the total, or it stops being free.

resource "aws_dynamodb_table" "users" {
  name         = "splitforce_users"
  billing_mode = "PROVISIONED"
  # Turn off (and apply) before an intentional terraform destroy.
  deletion_protection_enabled = true
  read_capacity               = 4
  write_capacity              = 4
  hash_key                    = "userId"

  attribute {
    name = "userId"
    type = "S"
  }
  attribute {
    name = "email"
    type = "S"
  }

  # Looked up on every authenticated request.
  global_secondary_index {
    name            = "email-index"
    hash_key        = "email"
    projection_type = "ALL"
    read_capacity   = 5
    write_capacity  = 4
  }

  point_in_time_recovery { enabled = false }
}

resource "aws_dynamodb_table" "groups" {
  name         = "splitforce_groups"
  billing_mode = "PROVISIONED"
  # Turn off (and apply) before an intentional terraform destroy.
  deletion_protection_enabled = true
  read_capacity               = 4
  write_capacity              = 3
  hash_key                    = "groupId"

  attribute {
    name = "groupId"
    type = "S"
  }
}

resource "aws_dynamodb_table" "group_members" {
  name         = "splitforce_group_members"
  billing_mode = "PROVISIONED"
  # Turn off (and apply) before an intentional terraform destroy.
  deletion_protection_enabled = true
  read_capacity               = 3
  write_capacity              = 3
  hash_key                    = "groupId"
  range_key                   = "userId"

  attribute {
    name = "groupId"
    type = "S"
  }
  attribute {
    name = "userId"
    type = "S"
  }

  global_secondary_index {
    name            = "user-groups-index"
    hash_key        = "userId"
    projection_type = "ALL"
    read_capacity   = 4
    write_capacity  = 3
  }
}

resource "aws_dynamodb_table" "expenses" {
  name         = "splitforce_expenses"
  billing_mode = "PROVISIONED"
  # Turn off (and apply) before an intentional terraform destroy.
  deletion_protection_enabled = true
  read_capacity               = 5
  write_capacity              = 8
  hash_key                    = "groupId"
  range_key                   = "expenseId"

  attribute {
    name = "groupId"
    type = "S"
  }
  attribute {
    name = "expenseId"
    type = "S"
  }
}

# OAuth 2.1 state for the remote MCP endpoint. Traffic is a handful of requests per login, so these
# use on-demand (PAY_PER_REQUEST) billing: it keeps the provisioned 25 RCU + 25 WCU free-tier budget
# above untouched, and at this volume the on-demand cost rounds to zero.
resource "aws_dynamodb_table" "oauth_clients" {
  name                        = "splitforce_oauth_clients"
  billing_mode                = "PAY_PER_REQUEST"
  deletion_protection_enabled = true
  hash_key                    = "clientId"

  attribute {
    name = "clientId"
    type = "S"
  }
}

resource "aws_dynamodb_table" "oauth_auth_codes" {
  name                        = "splitforce_oauth_auth_codes"
  billing_mode                = "PAY_PER_REQUEST"
  deletion_protection_enabled = true
  hash_key                    = "codeHash"

  attribute {
    name = "codeHash"
    type = "S"
  }

  # Authorization codes are single-use and short-lived; TTL sweeps any that are never redeemed.
  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
}

resource "aws_dynamodb_table" "oauth_keys" {
  name                        = "splitforce_oauth_keys"
  billing_mode                = "PAY_PER_REQUEST"
  deletion_protection_enabled = true
  hash_key                    = "id"

  attribute {
    name = "id"
    type = "S"
  }
}

locals {
  dynamodb_tables = [
    aws_dynamodb_table.users,
    aws_dynamodb_table.groups,
    aws_dynamodb_table.group_members,
    aws_dynamodb_table.expenses,
    aws_dynamodb_table.oauth_clients,
    aws_dynamodb_table.oauth_auth_codes,
    aws_dynamodb_table.oauth_keys,
  ]
}
