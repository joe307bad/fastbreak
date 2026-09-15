terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }

  # Remote state so GitHub Actions can plan/apply. The bucket and lock table
  # were created by hand (they have to exist before terraform can use them).
  backend "s3" {
    bucket         = "fastbreak-terraform-state-372400261891"
    key            = "fastbreak-charts/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "fastbreak-terraform-locks"
    encrypt        = true
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
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:PutItem",
          "dynamodb:GetItem",
          "dynamodb:UpdateItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = aws_dynamodb_table.file_timestamps.arn
      }
    ]
  })
}

# CloudFront Origin Access Control
resource "aws_cloudfront_origin_access_control" "fastbreak" {
  name                              = "fastbreak-oac"
  description                       = "OAC for Fastbreak S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# CloudFront Distribution
resource "aws_cloudfront_distribution" "fastbreak" {
  enabled             = true
  default_root_object = "index.json"
  comment             = "Fastbreak Charts JSON API"

  # S3 origin for static files
  origin {
    domain_name              = aws_s3_bucket.fastbreak.bucket_regional_domain_name
    origin_id                = "S3-${aws_s3_bucket.fastbreak.id}"
    origin_access_control_id = aws_cloudfront_origin_access_control.fastbreak.id
  }

  # Lambda origin for registry API
  origin {
    domain_name = replace(replace(aws_lambda_function_url.registry.function_url, "https://", ""), "/", "")
    origin_id   = "Lambda-registry"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # Lambda origin for scheduler-o11y API
  origin {
    domain_name = replace(replace(aws_lambda_function_url.scheduler_o11y.function_url, "https://", ""), "/", "")
    origin_id   = "Lambda-scheduler-o11y"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # Lambda origin for the OG image renderer
  origin {
    domain_name = replace(replace(aws_lambda_function_url.og_image.function_url, "https://", ""), "/", "")
    origin_id   = "Lambda-og-image"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # Default behavior - S3 static files
  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${aws_s3_bucket.fastbreak.id}"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  # Registry API behavior
  ordered_cache_behavior {
    path_pattern           = "/registry"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "Lambda-registry"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  # Scheduler o11y API behavior
  ordered_cache_behavior {
    path_pattern           = "/scheduler-o11y"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "Lambda-scheduler-o11y"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  # OG image behavior: the title/subtitle live in the query string, so cache on it
  ordered_cache_behavior {
    path_pattern           = "/og"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "Lambda-og-image"
    viewer_protocol_policy = "redirect-to-https"
    compress               = false

    forwarded_values {
      query_string = true
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 86400
    max_ttl     = 604800
  }

  # Only allow .json files
  custom_error_response {
    error_code         = 403
    response_code      = 404
    response_page_path = "/404.json"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# DynamoDB table for tracking file update timestamps
resource "aws_dynamodb_table" "file_timestamps" {
  name         = "fastbreak-file-timestamps"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "file_key"

  attribute {
    name = "file_key"
    type = "S"
  }

  tags = {
    Name = "fastbreak-file-timestamps"
  }
}

# Lambda IAM role
resource "aws_iam_role" "registry_lambda" {
  name = "fastbreak-registry-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Lambda IAM policy for DynamoDB and CloudWatch Logs
resource "aws_iam_role_policy" "registry_lambda" {
  name = "fastbreak-registry-lambda-policy"
  role = aws_iam_role.registry_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:Scan"
        ]
        Resource = aws_dynamodb_table.file_timestamps.arn
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# Archive the Lambda code
data "archive_file" "registry_lambda" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/registry"
  output_path = "${path.module}/lambda/registry.zip"
}

# Lambda function
resource "aws_lambda_function" "registry" {
  filename         = data.archive_file.registry_lambda.output_path
  function_name    = "fastbreak-registry"
  role             = aws_iam_role.registry_lambda.arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.registry_lambda.output_base64sha256
  runtime          = "nodejs20.x"
  timeout          = 10

  environment {
    variables = {
      DYNAMODB_TABLE = aws_dynamodb_table.file_timestamps.name
    }
  }
}

# Lambda Function URL for public access
resource "aws_lambda_function_url" "registry" {
  function_name      = aws_lambda_function.registry.function_name
  authorization_type = "NONE"

  cors {
    allow_origins = ["*"]
    allow_methods = ["GET"]
    allow_headers = ["*"]
  }
}

# Scheduler o11y Lambda: returns the per-script run records the pipeline
# writes to the same DynamoDB table under the scheduler-o11y namespace.
resource "aws_iam_role" "scheduler_o11y_lambda" {
  name = "fastbreak-scheduler-o11y-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "scheduler_o11y_lambda" {
  name = "fastbreak-scheduler-o11y-lambda-policy"
  role = aws_iam_role.scheduler_o11y_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:Scan"
        ]
        Resource = aws_dynamodb_table.file_timestamps.arn
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

data "archive_file" "scheduler_o11y_lambda" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/scheduler-o11y"
  output_path = "${path.module}/lambda/scheduler-o11y.zip"
}

resource "aws_lambda_function" "scheduler_o11y" {
  filename         = data.archive_file.scheduler_o11y_lambda.output_path
  function_name    = "fastbreak-scheduler-o11y"
  role             = aws_iam_role.scheduler_o11y_lambda.arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.scheduler_o11y_lambda.output_base64sha256
  runtime          = "nodejs20.x"
  timeout          = 10

  environment {
    variables = {
      DYNAMODB_TABLE = aws_dynamodb_table.file_timestamps.name
    }
  }
}

resource "aws_lambda_function_url" "scheduler_o11y" {
  function_name      = aws_lambda_function.scheduler_o11y.function_name
  authorization_type = "NONE"

  cors {
    allow_origins = ["*"]
    allow_methods = ["GET"]
    allow_headers = ["*"]
  }
}

# OG image Lambda: renders a dark title/subtitle card (satori + resvg) for
# social unfurls. Its node_modules are installed before packaging (see the
# deploy-infra job); it needs no AWS permissions beyond logging.
resource "aws_iam_role" "og_image_lambda" {
  name = "fastbreak-og-image-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "og_image_lambda" {
  name = "fastbreak-og-image-lambda-policy"
  role = aws_iam_role.og_image_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

data "archive_file" "og_image_lambda" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/og-image"
  output_path = "${path.module}/lambda/og-image.zip"
  excludes    = [".gitignore"]
}

resource "aws_lambda_function" "og_image" {
  filename         = data.archive_file.og_image_lambda.output_path
  function_name    = "fastbreak-og-image"
  role             = aws_iam_role.og_image_lambda.arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.og_image_lambda.output_base64sha256
  runtime          = "nodejs20.x"
  timeout          = 15
  memory_size      = 1024
}

resource "aws_lambda_function_url" "og_image" {
  function_name      = aws_lambda_function.og_image.function_name
  authorization_type = "NONE"

  cors {
    allow_origins = ["*"]
    allow_methods = ["GET"]
    allow_headers = ["*"]
  }
}

# S3 bucket policy to allow CloudFront access
resource "aws_s3_bucket_policy" "fastbreak" {
  bucket = aws_s3_bucket.fastbreak.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontServicePrincipal"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.fastbreak.arn}/*.json"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.fastbreak.arn
          }
        }
      }
    ]
  })
}
