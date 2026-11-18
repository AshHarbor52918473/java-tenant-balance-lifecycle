package example.infrai;

public final class FundingPolicy {
    public enum Decision { KEEP_ACTIVE, RECHARGE }

    public Decision decide(double balance, double operatingFloor) {
        return balance < operatingFloor ? Decision.RECHARGE : Decision.KEEP_ACTIVE;
    }
}
