package order;

import enums.Instrument;
import enums.Side;
import enums.Status;

import java.util.*;

public abstract class OrderBook {
    ArrayList<Order> bids;
    ArrayList<Order> offers;
    Map<Integer, Order> orders;
    final Instrument instrument;

    public OrderBook(Instrument instrument) {
        this.bids = new ArrayList<>();
        this.offers = new ArrayList<>();
        this.orders = new HashMap<>();
        this.instrument = instrument;
    }

    public ArrayList<Order> getBids() {
        return bids;
    }

    public ArrayList<Order> getOffers() {
        return offers;
    }

    public Map<Integer, Order> getOrders() {
        return orders;
    }

    public void addOrder(Order order) {
        this.orders.put(order.getOrderID(), order);
        if (order.getSide() == Side.BIDS) {
            this.bids.add(order);
            Collections.sort(bids, Collections.reverseOrder());
        } else {
            this.offers.add(order);
            Collections.sort(offers, Collections.reverseOrder());
        }
    }

    public void partFillOrder(int orderID, int quantity) {
        Order partfilledOrder = orders.get(orderID);
        if (partfilledOrder == null) return;

        partfilledOrder.setOrderDate(new Date());
        partfilledOrder.setQuantity(quantity);
        partfilledOrder.setStatus(Status.PARTFILLED);
        if (partfilledOrder.getSide() == Side.BIDS) {
            Collections.sort(this.bids, Collections.reverseOrder());
        } else {
            Collections.sort(this.offers, Collections.reverseOrder());
        }
    }

    public void fullyExecuteOrder(int orderID) {
        Order executedOrder = orders.get(orderID);
        if (executedOrder == null) return;
        executedOrder.setStatus(Status.FULLY_EXECUTED);
        if (executedOrder.getSide() == Side.BIDS) {
            bids.remove(executedOrder);
        } else {
            offers.remove(executedOrder);
        }
        orders.remove(orderID);
    }

}
