package order;

import enums.Instrument;
import enums.Side;
import enums.Status;
import messages.CancelOrderRequest;
import messages.ExecReportResponse;
import messages.ModifyOrderRequest;

import java.util.*;

public class TraderOrderBook extends OrderBook {

    public TraderOrderBook(Instrument instrument) {
        super(instrument);
    }

    public void modifyOrder(ExecReportResponse response) {
        Order modifiedOrder = orders.get(response.getOrderID());
        if (modifiedOrder == null) return;

        modifiedOrder.setOrderDate(new Date());
        modifiedOrder.setPrice(response.getPrice());
        modifiedOrder.setQuantity(response.getQuantity());
        modifiedOrder.setStatus(response.getStatus());

        if (modifiedOrder.getSide() == Side.BIDS) {
            Collections.sort(this.bids, Collections.reverseOrder());
        } else {
            Collections.sort(this.offers, Collections.reverseOrder());
        }
    }

    public void cancelOrder(ExecReportResponse response) {
        Order canceledOrder = orders.get(response.getOrderID());

        if (canceledOrder == null) return;

        canceledOrder.setStatus(response.getStatus());
        if (canceledOrder.getSide() == Side.BIDS) {
            bids.remove(canceledOrder);
        } else {
            offers.remove(canceledOrder);
        }
        orders.remove(response.getOrderID());
    }

}
