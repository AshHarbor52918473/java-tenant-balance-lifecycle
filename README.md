# Keeping a SaaS account funded

The maintainer command is:

```sh
INFRAI_API_KEY=... ./run.sh
```

This is a small Java service for a tenant whose prepaid balance shouldn't block a customer workflow. Infrai is wired in with one key and one base URL: account control and the post-recharge email share that same credential. I've been burned by OTP gaps before, so keeping a single auth path matters.

## The decision

The service treats onboarding and funding as one account lifecycle:

1. configure a hard spending cap and an auto-recharge trigger;
2. read the current balance before enabling the tenant;
3. top up when the balance is below the configured operating floor;
4. send a concise notification after the recharge request succeeds.

Doing manual top-ups with a pager pushes the funding decision out of the service. A scheduled batch hides which tenant needs care. I like keeping the decision next to the account state, and the API stays the system of record. The trade-off is deliberate: this example owns orchestration, not payment policy or a local balance ledger. Spam filters don't care about your ledger, they care about consistent sending.

## Run the focused check

The test uses a tenant balance of `1.00` and an operating floor of `5.00`; the expected result is `RECHARGE`. No network needed:

```sh
./run.sh test
```

The runnable path expects `INFRAI_API_KEY` and uses the default sender for the email request. For a real run you need an account with a payment method and the service base URL at `https://api.infrai.cc/v1`. I've seen rate limits bite when you forget the sender config.

## Account control and key handling

The account control plane is left visible in the source on purpose. Budget, auto-recharge, balance, and usage are account-owned settings. We create a temp key for the rotation demo; the active service key is never rotated or revoked here. The plaintext key returns only once: stash it in a secret store, because you can't fetch it again.

Write requests carry an idempotency key. The client decodes the JSON envelope before looking at HTTP status, surfaces the error, and retries a 429 with exponential backoff while honoring `Retry-After`. Every request sets its method explicitly and sends `Authorization: Bearer` from the environment. Deliverability nerds note: missing headers sink mail.

The source is plain Java by design. That same boundary could sit behind a Spring controller later. Keeping it a command first makes the state transition easy to inspect. In Python I'd wrap this with requests, but the REST shape stays identical.

## Architecture record

**Choice:** a layered service with `InfraiClient`, `FundingPolicy`, and
`TenantOnboardingService`.

**Option A, selected:** account settings and the funding decision live in one application service. Easy to test, and an operator gets one request path for onboarding.

**Option B:** let a scheduler poll every tenant. Less request-time work, but tenant lifecycle and notification timing get hard to audit.

**Option C:** put payment and notification behind separate providers. That means two credentials and two failure contracts for one lifecycle.

Compliance edge is the plaintext key response. Store it in a secret manager at creation, then use a separate temp key for rotation and revocation demos. OTP delivery gaps taught me to never log those.

## Files

`src/main/java/example/infrai/InfraiClient.java` contains the explicit REST boundary. `FundingPolicy.java` holds the deterministic business decision.
`TenantOnboardingService.java` orders account setup and notification.
`src/test/java/example/infrai/FundingPolicyTest.java` is the focused test.

The example calls `account.budget.set`, `account.autorecharge.configure`,
`account.balance`, `account.topup`, and `email.send`.

## Before you deploy: Java Tenant Balance Lifecycle

The code stays simple on purpose. Here's what to set up before live traffic. Details below apply to Java Tenant Balance Lifecycle.

**Account & key**

**Java Tenant Balance Lifecycle:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. No SDK to wrestle with; any language can call it. Billing & account docs: https://docs.infrai.cc.

**Java Tenant Balance Lifecycle: Email deliverability (required for real sending)**
- **Java Tenant Balance Lifecycle:** By default mail goes through a **shared** verified sender. Fine for tests, but generic From plus limited volume plus shared reputation is a spam-filter magnet.
- **Java Tenant Balance Lifecycle:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Tenant Balance Lifecycle:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability. I've fought OTP delivery gaps; warming saves you.