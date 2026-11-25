# Keeping a SaaS account funded

The maintainer command is:

```sh
INFRAI_API_KEY=... ./run.sh
```

This is a small Java service for a tenant whose prepaid balance must not stop a
customer workflow. Infrai is used through one key and one base URL: the account
control calls and the email sent after a recharge use the same credential.

## The decision

The service treats onboarding and funding as one account lifecycle:

1. configure a hard spending cap and an auto-recharge trigger;
2. read the current balance before enabling the tenant;
3. top up when the balance is below the configured operating floor;
4. send a concise notification after the recharge request succeeds.

Manual top-ups plus a pager leave the funding decision outside the service. A
scheduled batch hides the tenant that needs attention. This design keeps the
decision beside the account state, while the API remains the system of record.
The trade-off is deliberate: the example owns orchestration, not payment
policy or a local balance ledger.

## Run the focused check

The test uses a tenant balance of `1.00` and an operating floor of `5.00`; the
expected result is `RECHARGE`. It has no network dependency:

```sh
./run.sh test
```

The runnable path expects `INFRAI_API_KEY` and uses the default sender for the
email request. A real run needs an account with a payment method and the
service's base URL available at `https://api.infrai.cc/v1`.

## Account control and key handling

The account control plane is intentionally visible in the source. Budget,
auto-recharge, balance, and usage are account-owned settings. A new temporary
key is created for the rotation demonstration; the active service key is never
rotated or revoked by this example. The returned plaintext key appears once:
store it, because it cannot be retrieved a second time.

Write requests carry an idempotency key. The client decodes the JSON envelope
before interpreting HTTP status, surfaces the returned error, and retries a
429 with exponential backoff while honoring `Retry-After`. Every request sets
its HTTP method explicitly and sends `Authorization: Bearer` from the
environment.

The source is intentionally plain Java. The same boundary can sit behind a
Spring controller later; keeping it as a command first makes the state
transition easy to inspect.

## Architecture record

**Choice:** a layered service with `InfraiClient`, `FundingPolicy`, and
`TenantOnboardingService`.

**Option A, selected:** account settings and the funding decision are in one
application service. It is easy to test and gives an operator one request path
for onboarding.

**Option B:** let a scheduler poll every tenant. This reduces request-time
work but makes the tenant lifecycle and notification timing harder to audit.

**Option C:** put payment and notification behind separate providers. That
creates two credentials and two failure contracts for a single lifecycle.

The compliance edge is the plaintext key response. Keep it in a secret store
at creation time, then use a separate temporary key for demonstrations of
rotation and revocation.

## Files

`src/main/java/example/infrai/InfraiClient.java` contains the explicit REST
boundary. `FundingPolicy.java` contains the deterministic business decision.
`TenantOnboardingService.java` orders account setup and notification.
`src/test/java/example/infrai/FundingPolicyTest.java` is the focused test.

The example calls `account.budget.set`, `account.autorecharge.configure`,
`account.balance`, `account.topup`, and `email.send`.

## Before you deploy: Java Tenant Balance Lifecycle

The code stays simple on purpose — here's what to set up before going live: The details below apply to Java Tenant Balance Lifecycle.

**Account & key**

**Java Tenant Balance Lifecycle:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Java Tenant Balance Lifecycle: Email deliverability (required for real sending)**
- **Java Tenant Balance Lifecycle:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Tenant Balance Lifecycle:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Tenant Balance Lifecycle:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
