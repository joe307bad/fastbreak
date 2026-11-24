# The web site, as files.
#
# Separate from everything else in this module on purpose. The bucket and
# distribution above are the chart JSON API -- `index.json` for a root object,
# `/404.json` for an error page, Lambda behaviors on `/registry` and `/og`,
# and no TTL anywhere because the pipeline rewrites those objects all day. A
# site has the opposite shape, so it gets its own bucket and its own
# distribution rather than another behavior bolted onto that one.
#
# What lands here is `web/`'s static export -- `next build` with
# `output: "export"` -- synced by .github/workflows/deploy-web.yml once a day,
# after it has pulled the latest chart JSON out of the API bucket. Nothing in
# this stack runs per request, which is the whole point: the cost of serving
# the site is S3 storage plus CloudFront egress, and both are pennies.

variable "site_domain" {
  description = "Domain the static site is served at"
  type        = string
  default     = "fastbreak.joebad.com"
}

variable "site_bucket_name" {
  description = "Name of the S3 bucket holding the static site (must be globally unique)"
  type        = string
  default     = "fastbreak-joebad-com-site"
}

variable "site_hosted_zone" {
  description = "Route 53 hosted zone the site domain belongs to"
  type        = string
  default     = "joebad.com"
}

resource "aws_s3_bucket" "site" {
  bucket = var.site_bucket_name
}

# No versioning, unlike the chart bucket. Every object here is rebuilt from
# source and chart JSON every morning, so an old version is not a backup of
# anything -- it is a copy of a build that can be reproduced by re-running the
# workflow, and 1.5 GB of them per day is a real bill.
resource "aws_s3_bucket_public_access_block" "site" {
  bucket = aws_s3_bucket.site.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# The bucket is private; CloudFront reads it with an OAC and is the only thing
# that can. Serving from the S3 website endpoint instead would mean a public
# bucket and HTTP-only origin, and would buy only the directory-index behavior
# that site-rewrite.js already does at the edge for free.
resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "fastbreak-site-oac"
  description                       = "OAC for the Fastbreak static site bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontRead"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.site.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.site.arn
          }
        }
      }
    ]
  })
}

resource "aws_cloudfront_function" "site_rewrite" {
  name    = "fastbreak-site-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Map pretty URLs onto the export's <route>/index.html keys"
  publish = true
  code    = file("${path.module}/site-rewrite.js")
}

# CloudFront will only serve a custom domain under a certificate in us-east-1,
# which is where this whole module lives anyway. Validation is DNS, and the
# zone is in this same account, so terraform can write the record and wait for
# the certificate to issue without anyone touching a console.
resource "aws_acm_certificate" "site" {
  domain_name       = var.site_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

data "aws_route53_zone" "site" {
  name         = "${var.site_hosted_zone}."
  private_zone = false
}

resource "aws_route53_record" "site_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.site.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  zone_id         = data.aws_route53_zone.site.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "site" {
  certificate_arn         = aws_acm_certificate.site.arn
  validation_record_fqdns = [for record in aws_route53_record.site_cert_validation : record.fqdn]
}

resource "aws_cloudfront_distribution" "site" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  comment             = "Fastbreak static site (${var.site_domain})"
  aliases             = [var.site_domain]

  # US and Europe only. The cheapest price class, and the one that matches who
  # reads a site about North American sports.
  price_class = "PriceClass_100"

  origin {
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_id                = "S3-${aws_s3_bucket.site.id}"
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  # HTML and the RSC payloads beside it. Short-lived because the site is
  # rebuilt every morning and the deploy invalidates anyway; `max-age=0` on the
  # objects themselves (set by the sync) keeps browsers honest, while the edge
  # still absorbs a burst of traffic to the same page.
  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${aws_s3_bucket.site.id}"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.site_rewrite.arn
    }

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 300
    max_ttl     = 3600
  }

  # Next's build output is content-hashed, so a given URL under here can never
  # change meaning. Cache it for a year and skip the rewrite function: every
  # path in it already names a file.
  ordered_cache_behavior {
    path_pattern           = "/_next/static/*"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${aws_s3_bucket.site.id}"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 31536000
    max_ttl     = 31536000
  }

  # A private bucket answers a missing key with 403, not 404, so both have to
  # land on the export's own 404 page.
  custom_error_response {
    error_code            = 403
    response_code         = 404
    response_page_path    = "/404.html"
    error_caching_min_ttl = 300
  }

  custom_error_response {
    error_code            = 404
    response_code         = 404
    response_page_path    = "/404.html"
    error_caching_min_ttl = 300
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.site.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

resource "aws_route53_record" "site_a" {
  zone_id = data.aws_route53_zone.site.zone_id
  name    = var.site_domain
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.site.domain_name
    zone_id                = aws_cloudfront_distribution.site.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "site_aaaa" {
  zone_id = data.aws_route53_zone.site.zone_id
  name    = var.site_domain
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.site.domain_name
    zone_id                = aws_cloudfront_distribution.site.hosted_zone_id
    evaluate_target_health = false
  }
}
