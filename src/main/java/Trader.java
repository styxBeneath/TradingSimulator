import akka.event.Logging;
import akka.event.LoggingAdapter;
import enums.Instrument;
import enums.ExecType;
import enums.Side;
import enums.Status;
import messages.*;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import order.Order;
import order.TraderOrderBook;

import java.time.Duration;
import java.util.*;

import static java.lang.Math.round;

class Trader extends AbstractActorWithTimers {
    private final int traderID;
    private final ActorRef matchEngine;
    private Map<Instrument, TraderOrderBook> orderBooks;
    private Map<Integer, Order> myOrders;
    private Random rgen;
    private Instrument[] instruments;
    private ArrayList<Instrument> activeInstruments;
    private List<ExecReportResponse> reports;
    private final LoggingAdapter log;

    private static final class NotifyTrader {
    }

    public Trader(ActorRef matchEngine, int traderID) {
        this.matchEngine = matchEngine;
        this.traderID = traderID;
        this.orderBooks = new HashMap<>();
        this.myOrders = new HashMap<>();
        this.rgen = new Random();
        this.instruments = Instrument.values();
        this.activeInstruments = new ArrayList<>();
        this.reports = new ArrayList<>();
        this.log = Logging.getLogger(getContext().getSystem(), this);

        matchEngine.tell(new SubscriptionRequest(this.traderID), getSelf());
    }

    public static Props props(ActorRef matchEngine, int traderID) {
        return Props.create(Trader.class, matchEngine, traderID);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(ExecReportResponse.class, response -> {
            onExecReportResponse(response);
        }).match(TransactionComplete.class, m -> {
            onTransactionComplete();
        }).match(TradeMessage.class, tradeMessage -> {
            onTradeMessage(tradeMessage);
        }).match(NotifyTrader.class, m -> {
            onNotifyTrader();
        }).match(SubscriptionResponse.class, m -> {
            getTimers().startTimerWithFixedDelay("KEY", new NotifyTrader(), Duration.ofSeconds(3));

        }).build();
    }


    private void onExecReportResponse(ExecReportResponse response) {

        if (response.getStatus() == Status.REJECTED) {
            return;
        }

        this.reports.add(response);
    }

    //iterating through ERs to update the state of order book
    private void onTransactionComplete() {
        for (int i = 0; i < this.reports.size(); i++) {
            updateOrders(this.reports.get(i));
        }
        this.reports.clear();
    }

    // updating orderbooks according to ExecReportResponse
    private void updateOrders(ExecReportResponse response) {
        if (response.getExecType() == ExecType.ADD) {
            Order newOrder = createNewOrder(response);
            addNewOrder(newOrder);
        } else if (response.getExecType() == ExecType.UPDATE) {
            orderBooks.get(response.getInstrument()).modifyOrder(response);
        } else if (response.getExecType() == ExecType.REMOVE) {
            removeOrder(response);
        }
    }

    // creating new order according to ER
    private Order createNewOrder(ExecReportResponse response) {
        Order newOrder = new Order(response.getTraderID(), response.getOrderID(), response.getSide(), response.getInstrument());
        newOrder.setOrderDate(response.getDate());
        newOrder.setQuantity(response.getQuantity());
        newOrder.setPrice(response.getPrice());
        newOrder.setStatus(response.getStatus());
        return newOrder;
    }

    //adding new order to orderbooks
    private void addNewOrder(Order newOrder) {

        //case of new instrument
        if (!orderBooks.containsKey(newOrder.getInstrument())) {
            activeInstruments.add(newOrder.getInstrument());

            orderBooks.put(newOrder.getInstrument(), new TraderOrderBook(newOrder.getInstrument()));
        }

        orderBooks.get(newOrder.getInstrument()).addOrder(newOrder);

        if (newOrder.getTraderID() == this.traderID) {
            myOrders.put(newOrder.getOrderID(), newOrder);
        }
    }

    // canceling or fully executing order according to ER
    private void removeOrder(ExecReportResponse response) {
        orderBooks.get(response.getInstrument()).cancelOrder(response);
        myOrders.remove(response.getOrderID());
    }

    private void onTradeMessage(TradeMessage tradeMessage) {
        log.info("Trade Message: " + tradeMessage.toString());
    }

    private void onNotifyTrader() {
        Request request = generateRandomRequest();

        if (request != null) {
            matchEngine.tell(request, getSelf());
        }
    }

    //generates random requests of trader for ME according to orderbooks' state
    private Request generateRandomRequest() {
        switch (rgen.nextInt(5)) {
            case 0:
                return randomNewOrderRequest();
            case 1:
                return newOrderRequestMatchingBestOrder();
            case 2:
                return randomModifyOrderRequest();
            case 3:
                return modifyOrderRequestMatchingBestOrder();
            default:
                return randomCancelOrderRequest();
        }
    }

    //randomly generates NOR
    private NewOrderRequest randomNewOrderRequest() {
        Side side = getSide();
        Instrument instrument = instruments[rgen.nextInt(instruments.length)];
        int quantity = rgen.nextInt(10) + 1;
        double price = rgen.nextInt(10) + (double) round(rgen.nextDouble() * 10) / 10;
        NewOrderRequest request = new NewOrderRequest(side, instrument, quantity, price, new Date(), this.traderID);
        return request;
    }

    //generates the side of NOR randomly
    private Side getSide() {
        Side side = Side.OFFERS;
        if (rgen.nextBoolean()) {
            side = Side.BIDS;
        }
        return side;
    }

    //randomly generates MOR
    private ModifyOrderRequest randomModifyOrderRequest() {
        Object[] keys = myOrders.keySet().toArray();

        if (keys.length != 0) {
            int orderID = (int) keys[rgen.nextInt(keys.length)];
            int quantity = 1 + rgen.nextInt(10);
            double price = rgen.nextInt(10) + (double) round(rgen.nextDouble() * 10) / 10;
            ModifyOrderRequest request = new ModifyOrderRequest(orderID, this.traderID, quantity, price);
            return request;
        }
        return null;
    }

    //randomly generates COR
    private CancelOrderRequest randomCancelOrderRequest() {
        Object[] keys = myOrders.keySet().toArray();
        if (keys.length != 0) {
            CancelOrderRequest request = new CancelOrderRequest((int) keys[rgen.nextInt(keys.length)], this.traderID);
            return request;
        }
        return null;
    }


    // generates NOR that matches random best order
    private Request newOrderRequestMatchingBestOrder() {
        Order bestOrder = getRandomBestOrder();
        if (bestOrder == null) return null;

        Side side;
        if (bestOrder.getSide() == Side.BIDS) side = Side.OFFERS;
        else side = Side.BIDS;

        Instrument instrument = bestOrder.getInstrument();
        int quantity = bestOrder.getQuantity();
        double price = bestOrder.getPrice();
        return new NewOrderRequest(side, instrument, quantity, price, new Date(), this.traderID);
    }

    //returns random best order from order books
    private Order getRandomBestOrder() {
        if (activeInstruments.isEmpty()) {
            return null;
        }

        Instrument instrument = activeInstruments.get(rgen.nextInt(activeInstruments.size()));

        if (orderBooks.get(instrument).getBids().isEmpty() && orderBooks.get(instrument).getOffers().isEmpty()) {
            return null;
        }

        if (orderBooks.get(instrument).getOffers().isEmpty()) {
            return orderBooks.get(instrument).getBids().get(0);
        }

        if (orderBooks.get(instrument).getBids().isEmpty()) {
            return orderBooks.get(instrument).getOffers().get(0);
        }

        switch (rgen.nextInt(2)) {
            case 0:
                return orderBooks.get(instrument).getBids().get(0);
            default:
                return orderBooks.get(instrument).getOffers().get(0);
        }
    }

    //generates MOR that matches random best order
    private Request modifyOrderRequestMatchingBestOrder() {

        Order bestOrder = getRandomBestOrder();
        if (bestOrder == null) return null;

        Order myOrder = getOrderToMatchBestOrder(bestOrder.getInstrument());
        if (myOrder == null) return null;

        return new ModifyOrderRequest(myOrder.getOrderID(), this.traderID, bestOrder.getQuantity(), bestOrder.getPrice());
    }

    // chooses random order from myOrders to match best order lately
    private Order getOrderToMatchBestOrder(Instrument instrument) {
        List<Order> orders = new ArrayList<>();
        for (int orderID : myOrders.keySet()) {
            if (myOrders.get(orderID).getInstrument() == instrument) {
                orders.add(myOrders.get(orderID));
            }
        }
        if (orders.size() > 0) {
            return orders.get(rgen.nextInt(orders.size()));
        }
        return null;
    }

}
