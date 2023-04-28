package messages;

import enums.Instrument;
import enums.Side;

public final class ModifyOrderRequest extends Request {

    private final int orderID;
    private final int traderID;
    private final int quantity;
    private final double price;

    public ModifyOrderRequest(int orderID, int traderID, int quantity, double price) {
        this.orderID = orderID;
        this.traderID = traderID;
        this.quantity = quantity;
        this.price = price;
    }


    public int getOrderID() {
        return orderID;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public int getTraderID() {
        return traderID;
    }

    @Override
    public String toString() {
        return "ModifyOrderRequest{" +
                "orderID=" + orderID +
                ", traderID=" + traderID +
                ", quantity=" + quantity +
                ", price=" + price +
                '}';
    }
}
