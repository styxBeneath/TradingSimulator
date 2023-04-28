package messages;

public final class SubscriptionRequest extends Request {

    private final int traderID;

    public SubscriptionRequest(int traderID) {
        this.traderID = traderID;
    }

    public int getTraderID() {
        return traderID;
    }

}
