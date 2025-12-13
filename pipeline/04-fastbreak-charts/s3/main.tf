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

  origin {
    domain_name              = aws_s3_bucket.fastbreak.bucket_regional_domain_name
    origin_id                = "S3-${aws_s3_bucket.fastbreak.id}"
    origin_access_control_id = aws_cloudfront_origin_access_control.fastbreak.id
  }

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
