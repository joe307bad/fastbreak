terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

resource "aws_s3_bucket" "fastbreak" {
  bucket = var.bucket_name
}

resource "aws_s3_bucket_versioning" "fastbreak" {
  bucket = aws_s3_bucket.fastbreak.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_iam_user" "s3_user" {
  name = "fastbreak-s3-user"
}

resource "aws_iam_access_key" "s3_user" {
  user = aws_iam_user.s3_user.name
}

resource "aws_iam_user_policy" "s3_access" {
  name = "S3FullAccessPolicy"
  user = aws_iam_user.s3_user.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "s3:*"
        Resource = [
          aws_s3_bucket.fastbreak.arn,
          "${aws_s3_bucket.fastbreak.arn}/*"
        ]
      }
    ]
  })
}
