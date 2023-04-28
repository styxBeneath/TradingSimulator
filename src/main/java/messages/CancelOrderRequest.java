package messages;

public final class CancelOrderRequest extends Request {
    private final int orderID;
    private final int traderID;

    public CancelOrderRequest(int orderID, int traderID) {
        this.orderID = orderID;
        this.traderID = traderID;
    }

    public int getOrderID() {
        return orderID;
    }

    public int getTraderID() {
        return traderID;
    }

    @Override
    public String toString() {
        return "CancelOrderRequest{" +
                "orderID=" + orderID +
                ", traderID=" + traderID +
                '}';
    }
}
