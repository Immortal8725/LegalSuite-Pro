# LegalSuite Pro AWS hosting checklist (Cape Town / af-south-1)

Plan for the South Africa production pilot in AWS Cape Town (`af-south-1`). This file is a checklist only. Writing it did not create AWS resources, change application code, or store secrets.

## Shape

Run the Spring Boot API in `af-south-1` on Elastic Beanstalk (`t3.small` or `t4g.small`, Java 21), or on a small EC2 instance in the same region. Put it behind an Application Load Balancer with an ACM certificate on a hostname such as `api.yourfirm.example`.

That HTTPS URL is `TWILIO_PUBLIC_BASE_URL`. Twilio uses it for the call bridge and status callbacks.

Use RDS Postgres `db.t4g.micro`, single-AZ, in a private subnet. Point Spring at Postgres with `SPRING_PROFILES_ACTIVE=postgres`. Local development stays on H2.

Store documents in a private S3 bucket. Attach an IAM role to the API so it can read and write that bucket.

Keep secrets in Parameter Store or Secrets Manager: the Twilio account SID and auth token, the JWT secret, the database URL, and the other runtime values. Never commit secrets to the repo.

Leave Next.js on Vercel for the pilot. Set `NEXT_PUBLIC_API_URL` to the AWS API HTTPS URL.

Twilio webhooks must hit the API host only. They must not be sent to Vercel.

## Cost ballpark

Roughly USD 60-90 per month in Cape Town (`af-south-1`) for a small solo pilot, before tax and before Twilio usage. Exchange rates change the rand amount.

| Piece | Rough monthly USD |
| --- | --- |
| Compute (Elastic Beanstalk or a small EC2) | ~20 |
| Application Load Balancer | ~20 |
| RDS `db.t4g.micro` plus storage | ~15, plus storage |
| S3 and data transfer | a few dollars |
| ACM certificate used with the load balancer | free |

## Minimum to get Place call working end to end

Place call is the outbound path in [pull request 4](https://github.com/Immortal8725/LegalSuite-Pro/pull/4) (firm caller ID for PSTN callback calls).

1. Verify `+27606443033` as a Twilio Outgoing Caller ID. The national form is 060 644 3033. E.164 drops the leading 0 after `+27`.
2. Deploy the pull request 4 API with public HTTPS.
3. Set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL`, and optionally `TWILIO_VOICE_FROM`.
4. Connect Twilio in Integrations in the app.
5. Place a call to a reachable phone.

No DID purchase and no South African regulatory bundle are required for this outbound path. Inbound PSTN still needs a Twilio number later.

## Order of operations

1. Finish verifying the caller ID.
2. Deploy or merge the pull request 4 API to Cape Town with TLS and the environment variables above.
3. Stand up RDS and point Spring at Postgres.
4. Create the private S3 bucket and wire document storage through the API IAM role.
5. Point Vercel at the API URL with `NEXT_PUBLIC_API_URL`.
6. Smoke-test Place call.
7. Then harden: backups, forced 2FA, and monitoring.

## Non-goals

This is a plan checklist only. No AWS resources were created when this doc was written.
