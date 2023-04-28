package messages;

import enums.Instrument;
import enums.Side;

import java.util.Date;

public final class NewOrderRequest extends Request {

    private final int traderID;
    private final Side side;
    private final Instrument instrument;
    private final int quantity;
    private final double price;
    private final Date date;


    public NewOrderRequest(Side side, Instrument instrument, int quantity, double price, Date date, int traderID) {
        this.side = side;
        this.instrument = instrument;
        this.quantity = quantity;
        this.price = price;
        this.date = date;
        this.traderID = traderID;
    }

    public Side getSide() {
        return side;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public Date getDate() {
        return date;
    }

    public int getTraderID() {
        return traderID;
    }

    @Override
    public String toString() {
        return "NewOrderRequest{" +
                "traderID=" + traderID +
                ", side=" + side +
                ", instrument=" + instrument +
                ", quantity=" + quantity +
                ", price=" + price +
                ", date=" + date +
                '}';
    }
}
