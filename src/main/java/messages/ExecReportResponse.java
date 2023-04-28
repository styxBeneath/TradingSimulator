package messages;

import java.util.Date;

import enums.*;
import order.Order;

public final class ExecReportResponse extends Response {

    private final int orderID;
    private final int traderID;
    private final Side side;
    private final Instrument instrument;
    private final int quantity;
    private final double price;
    private final Status status;
    private final ExecType execType;
    private final RejectionReason rejectionReason;
    private final Date orderDate;

    // on successful requests
    public ExecReportResponse(Order order, ExecType execType) {
        this.instrument = order.getInstrument();
        this.side = order.getSide();
        this.traderID = order.getTraderID();
        this.quantity = order.getQuantity();
        this.price = order.getPrice();
        this.status = order.getStatus();
        this.orderID = order.getOrderID();
        this.orderDate = order.getOrderDate();
        this.execType = execType;
        this.rejectionReason = null;
    }

    //on rejected new order request
    public ExecReportResponse(NewOrderRequest request, RejectionReason rejectionReason) {
        this.instrument = request.getInstrument();
        this.side = request.getSide();
        this.traderID = request.getTraderID();
        this.quantity = request.getQuantity();
        this.price = request.getPrice();
        this.status = Status.REJECTED;
        this.orderID = -1;
        this.orderDate = request.getDate();
        this.execType = ExecType.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    // on rejected modify request
    public ExecReportResponse(ModifyOrderRequest request, RejectionReason rejectionReason) {
        this.instrument = null;
        this.side = null;
        this.traderID = request.getTraderID();
        this.quantity = request.getQuantity();
        this.price = request.getPrice();
        this.status = Status.REJECTED;
        this.orderID = -1;
        this.orderDate = new Date();
        this.execType = ExecType.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    //on rejected cancel request
    public ExecReportResponse(CancelOrderRequest request, RejectionReason rejectionReason) {
        this.instrument = null;
        this.side = null;
        this.traderID = request.getTraderID();
        this.quantity = 0;
        this.price = 0;
        this.status = Status.REJECTED;
        this.orderID = -1;
        this.orderDate = new Date();
        this.execType = ExecType.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    public Instrument getInstrument() {
        Instrument res = this.instrument;
        return res;
    }

    public Side getSide() {
        Side res = this.side;
        return res;
    }

    public int getTraderID() {
        return traderID;
    }

    public int getQuantity() {
        int res = this.quantity;
        return res;
    }

    public double getPrice() {
        double res = this.price;
        return res;
    }

    public Status getStatus() {
        Status res = this.status;
        return res;
    }

    public int getOrderID() {
        int res = this.orderID;
        return res;
    }

    public Date getDate() {
        return orderDate;
    }

    public ExecType getExecType() {
        return execType;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    @Override
    public String toString() {
        return "ExecReportResponse{" +
                "orderID=" + orderID +
                ", traderID=" + traderID +
                ", side=" + side +
                ", instrument=" + instrument +
                ", quantity=" + quantity +
                ", price=" + price +
                ", status=" + status +
                ", execType=" + execType +
                ", rejectionReason=" + rejectionReason +
                ", orderDate=" + orderDate +
                '}';
    }
}
