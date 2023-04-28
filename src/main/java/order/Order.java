package order;

import enums.Instrument;
import enums.Side;
import enums.Status;

import java.util.Date;

public class Order implements Comparable<Order> {

    private final int orderID;
    private final int traderID;
    private final Side side;
    private final Instrument instrument;
    private int quantity;
    private double price;
    private Status status;
    private Date orderDate;

    public Order(int traderID, int orderID, Side side,Instrument instrument) {
        this.traderID = traderID;
        this.orderID = orderID;
        this.side = side;
        this.instrument = instrument;
        this.orderDate = new Date();
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Date getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

    public int getTraderID() {
        return traderID;
    }

    public int getOrderID() {
        return orderID;
    }

    public Side getSide() {
        return side;
    }

    @Override
    public int compareTo(Order o) {
        if (this.getSide() == Side.BIDS) {
            if (this.price > o.price) {
                return 1;
            } else if (this.price < o.price) {
                return -1;
            } else if (this.orderDate.compareTo(o.orderDate) > 0) {
                return -1;
            } else return 1;
        } else {
            if (this.price > o.price) {
                return -1;
            } else if (this.price < o.price) {
                return 1;
            } else if (this.orderDate.compareTo(o.orderDate) > 0) {
                return -1;
            } else return 1;
        }
    }

    //generates the copy of Order
    public Order clone(){
        Order clone = new Order(this.traderID,this.orderID,this.side,this.instrument);
        clone.setQuantity(this.quantity);
        clone.setPrice(this.price);
        clone.setStatus(this.status);
        clone.setOrderDate(this.orderDate);
        return clone;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderID=" + orderID +
                ", traderID=" + traderID +
                ", side=" + side +
                ", instrument=" + instrument +
                ", quantity=" + quantity +
                ", price=" + price +
                ", status=" + status +
                ", orderDate=" + orderDate +
                '}';
    }
}
