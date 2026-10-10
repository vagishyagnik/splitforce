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

locals {
  dynamodb_tables = [
    aws_dynamodb_table.users,
    aws_dynamodb_table.groups,
    aws_dynamodb_table.group_members,
    aws_dynamodb_table.expenses,
  ]
}
