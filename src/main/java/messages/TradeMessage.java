package messages;

import enums.Instrument;

public final class TradeMessage extends Response {

    private final int bidOrderID;
    private final int offerOrderID;
    private final Instrument instrument;
    private final double tradePrice;
    private final int tradeQuantity;

    public TradeMessage(int bidOrderID, int offerOrderID, double tradePrice, int tradeQuantity, Instrument instrument) {
        this.bidOrderID = bidOrderID;
        this.offerOrderID = offerOrderID;
        this.tradePrice = tradePrice;
        this.tradeQuantity = tradeQuantity;
        this.instrument = instrument;
    }

    public int getBidOrderID() {
        return bidOrderID;
    }

    public int getOfferOrderID() {
        return offerOrderID;
    }

    public double getTradePrice() {
        return tradePrice;
    }

    public int getTradeQuantity() {
        return tradeQuantity;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    @Override
    public String toString() {
        return "TradeMessage{" +
                "bidOrderID=" + bidOrderID +
                ", offerOrderID=" + offerOrderID +
                ", instrument=" + instrument +
                ", tradePrice=" + tradePrice +
                ", tradeQuantity=" + tradeQuantity +
                '}';
    }
}
