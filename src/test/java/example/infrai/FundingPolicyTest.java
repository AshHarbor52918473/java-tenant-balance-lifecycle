package example.infrai;

public final class FundingPolicyTest {
    public static void main(String[] args) {
        FundingPolicy policy = new FundingPolicy();
        if (policy.decide(1.0, 5.0) != FundingPolicy.Decision.RECHARGE) throw new AssertionError("low balance must recharge");
        if (policy.decide(5.0, 5.0) != FundingPolicy.Decision.KEEP_ACTIVE) throw new AssertionError("floor balance stays active");
        System.out.println("FundingPolicyTest passed");
    }
}
