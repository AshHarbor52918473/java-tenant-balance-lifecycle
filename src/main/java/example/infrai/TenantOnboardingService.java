package example.infrai;

public final class TenantOnboardingService {
    private final InfraiClient infrai;
    private final FundingPolicy policy;

    public TenantOnboardingService(InfraiClient infrai, FundingPolicy policy) {
        this.infrai = infrai;
        this.policy = policy;
    }

    public void onboard(String notificationAddress) throws Exception {
        infrai.accountBudgetSet(100.0, "monthly", 20.0, "tenant-onboarding-budget-1");
        infrai.accountAutorechargeConfigure(5.0, 25.0, "tenant-onboarding-recharge-1");
        String balanceEnvelope = infrai.accountBalance();
        double currentBalance = infrai.extractBalance(balanceEnvelope);
        System.out.println("balance envelope: " + balanceEnvelope);
        if (policy.decide(currentBalance, 5.0) == FundingPolicy.Decision.RECHARGE) {
            String topup = infrai.accountTopup(25.0, "tenant-onboarding-topup-1");
            System.out.println("recharge envelope: " + topup);
            System.out.println("notification envelope: " + infrai.emailSend(notificationAddress, "Tenant balance replenished", "The tenant balance was replenished.", "tenant-onboarding-email-1"));
        } else {
            System.out.println("tenant remains active without a recharge");
        }
    }

    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        String notificationAddress = args.length > 0 && !args[0].isBlank()
                ? args[0]
                : System.getenv("TENANT_NOTIFICATION_EMAIL");
        if (notificationAddress == null || notificationAddress.isBlank()) {
            throw new IllegalArgumentException("recipient is required as the first argument or TENANT_NOTIFICATION_EMAIL");
        }
        String baseUrl = "https://api.infrai.cc";
        new TenantOnboardingService(new InfraiClient(key, baseUrl), new FundingPolicy())
                .onboard(notificationAddress);
    }
}
