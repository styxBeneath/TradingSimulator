package order;

import enums.Instrument;
import enums.Side;
import enums.Status;
import messages.CancelOrderRequest;
import messages.ExecReportResponse;
import messages.ModifyOrderRequest;

import java.util.*;

public class MEOrderBook extends OrderBook {

    public MEOrderBook(Instrument instrument) {
        super(instrument);
    }

    public void modifyOrder(ModifyOrderRequest request) {
        Order modifiedOrder = orders.get(request.getOrderID());
        if (modifiedOrder == null) return;

        modifiedOrder.setOrderDate(new Date());
        modifiedOrder.setPrice(request.getPrice());
        modifiedOrder.setQuantity(request.getQuantity());

        if (modifiedOrder.getSide() == Side.BIDS) {
            Collections.sort(this.bids, Collections.reverseOrder());
        } else {
            Collections.sort(this.offers, Collections.reverseOrder());
        }
    }

    public void cancelOrder(int orderID) {
        Order canceledOrder = orders.get(orderID);
        if (canceledOrder == null) return;

        if (canceledOrder.getSide() == Side.BIDS) {
            bids.remove(canceledOrder);
        } else {
            offers.remove(canceledOrder);
        }

        orders.remove(orderID);
    }

}
