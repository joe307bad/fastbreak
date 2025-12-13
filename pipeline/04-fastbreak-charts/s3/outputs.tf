output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.fastbreak.id
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.fastbreak.arn
}

output "access_key_id" {
  description = "Access key ID for the IAM user"
  value       = aws_iam_access_key.s3_user.id
}

output "secret_access_key" {
  description = "Secret access key for the IAM user"
  value       = aws_iam_access_key.s3_user.secret
  sensitive   = true
}
