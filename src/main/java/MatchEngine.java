
import akka.actor.AbstractLoggingActor;
import akka.actor.Terminated;
import enums.Instrument;
import enums.ExecType;
import enums.RejectionReason;
import enums.Status;
import messages.*;
import akka.actor.ActorRef;
import akka.actor.Props;
import order.Order;
import order.MEOrderBook;

import java.util.*;

import static java.lang.Math.min;

public class MatchEngine extends AbstractLoggingActor {


    private Map<Integer, ActorRef> subscribers;
    private Map<Integer, ActorRef> nonSubscribers;
    private ArrayList<ActorRef> nonSubscribersTonotify;
    private Map<Integer, Order> orders;
    private Map<Instrument, MEOrderBook> orderBooks;
    private ArrayList<Response> reports;
    private int numOfOrderID;

    public MatchEngine() {
        subscribers = new HashMap<>();
        nonSubscribers = new HashMap<>();
        nonSubscribersTonotify = new ArrayList<>();
        orders = new HashMap<>();
        orderBooks = new HashMap<>();
        reports = new ArrayList<>();
        numOfOrderID = 0;
    }

    public static Props props() {
        return Props.create(MatchEngine.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(NewOrderRequest.class, request -> {
            onNewOrderRequest(request, getSender());
        }).match(ModifyOrderRequest.class, request -> {
            onModifyOrderRequest(request, getSender());
        }).match(CancelOrderRequest.class, request -> {
            onCancelOrderRequest(request, getSender());
        }).match(SubscriptionRequest.class, request -> {
            onSubscriptionRequest(getSender(), request);
        }).match(Terminated.class, this::onTerminationMessage).build();
    }

    private void onNewOrderRequest(NewOrderRequest request, ActorRef trader) {
        log().info("Received New Order Request: " + request.toString());
        ExecReportResponse response;


        // if NOR is valid, ME creates NO, adds NO and tries to match the best orders
        RejectionReason rejectionReason = newOrderRequestRejectionReason(request);
        if (rejectionReason == null) {

            if (!subscribers.containsKey(request.getTraderID()) && !nonSubscribers.containsKey(request.getTraderID())) {
                nonSubscribers.put(request.getTraderID(), trader);
                context().watch(trader);
            }

            Order newOrder = generateNewOrder(request);
            addNewOrder(newOrder);

            response = new ExecReportResponse(newOrder, ExecType.ADD);
            log().info("Distributing Execution Report: " + response.toString() + " To " + subscribers.size() + " Subscribers");
            reports.add(response);

            if (nonSubscribers.containsKey(request.getTraderID())) {
                nonSubscribersTonotify.add(nonSubscribers.get(request.getTraderID()));
                trader.tell(response, getSelf());
            }
            notifySubscribers(response);
            matchOrders(request.getInstrument());
        } else {
            log().info("New Order Request Rejection Reason: " + rejectionReason.name());
            response = new ExecReportResponse(request, rejectionReason);
            trader.tell(response, getSelf());
        }
    }

    //checks whether NOR is valid
    private RejectionReason newOrderRequestRejectionReason(NewOrderRequest request) {
        if (request.getPrice() <= 0) {
            return RejectionReason.INVALID_PRICE;
        }

        if (request.getQuantity() <= 0) {
            return RejectionReason.INVALID_QUANTITY;
        }

        return null;
    }

    //generates NO according to NOR
    private Order generateNewOrder(NewOrderRequest newRequest) {

        Order newOrder = new Order(newRequest.getTraderID(), numOfOrderID, newRequest.getSide(), newRequest.getInstrument());
        newOrder.setPrice(newRequest.getPrice());
        newOrder.setOrderDate(newRequest.getDate());
        newOrder.setQuantity(newRequest.getQuantity());
        newOrder.setStatus(Status.ACTIVE);
        return newOrder;
    }

    //adds NO to order books
    private void addNewOrder(Order newOrder) {

        orders.put(numOfOrderID, newOrder);
        numOfOrderID++;


        // Case of new Instrument
        if (!orderBooks.containsKey(newOrder.getInstrument())) {
            orderBooks.put(newOrder.getInstrument(), new MEOrderBook(newOrder.getInstrument()));
        }

        orderBooks.get(newOrder.getInstrument()).addOrder(newOrder);
    }

    private void onModifyOrderRequest(ModifyOrderRequest request, ActorRef trader) {
        log().info("Received Modify Order Request: " + request.toString());
        ExecReportResponse response;

        // if MOR is valid, ME modifies order and tries to match the best orders
        RejectionReason rejectionReason = modifyOrderRequestRejectionReason(request, trader);
        if (rejectionReason == null) {
            Order modifiedOrder = orders.get(request.getOrderID());
            orderBooks.get(modifiedOrder.getInstrument()).modifyOrder(request);

            response = new ExecReportResponse(modifiedOrder, ExecType.UPDATE);
            reports.add(response);
            log().info("Distributing Execution Report: " + response.toString() + " To " + subscribers.size() + " Subscribers");

            if (nonSubscribers.containsKey(request.getTraderID())) {
                nonSubscribersTonotify.add(nonSubscribers.get(request.getTraderID()));
                trader.tell(response, getSelf());
            }
            notifySubscribers(response);
            matchOrders(modifiedOrder.getInstrument());
        } else {
            log().info("Modify Order Request Rejection Reason: " + rejectionReason.name());
            response = new ExecReportResponse(request, rejectionReason);
            trader.tell(response, getSelf());
        }

    }

    //checks whether MOR is valid
    private RejectionReason modifyOrderRequestRejectionReason(ModifyOrderRequest request, ActorRef trader) {


        if (!subscribers.containsKey(request.getTraderID()) && !nonSubscribers.containsKey(request.getTraderID())) {
            return RejectionReason.INVALID_TRADER_ID;
        }

        if (subscribers.containsKey(request.getTraderID()) &&
                subscribers.get(request.getTraderID()) != trader
        ) {
            return RejectionReason.INVALID_TRADER_ID;
        }

        if (nonSubscribers.containsKey(request.getTraderID()) &&
                !nonSubscribers.get(request.getTraderID()).equals(trader)
        ) {
            return RejectionReason.INVALID_TRADER_ID;
        }

        if (!orders.containsKey(request.getOrderID()) ||
                orders.get(request.getOrderID()).getTraderID() != request.getTraderID()
        ) {
            return RejectionReason.INVALID_ORDER_ID;
        }

        if (request.getPrice() <= 0) {
            return RejectionReason.INVALID_PRICE;
        }

        if (request.getQuantity() <= 0) {
            return RejectionReason.INVALID_QUANTITY;
        }

        return null;
    }

    private void onCancelOrderRequest(CancelOrderRequest request, ActorRef trader) {
        log().info("Received Cancel Order Request: " + request.toString());

        ExecReportResponse response;

        RejectionReason rejectionReason = cancelOrderRequestRejectionReason(request, trader);
        //if COR is valid, ME cancels order
        if (rejectionReason == null) {
            Order canceledOrder = orders.get(request.getOrderID());
            canceledOrder.setStatus(Status.CANCELED);
            orderBooks.get(canceledOrder.getInstrument()).cancelOrder(request.getOrderID());
            orders.remove(request.getOrderID());

            response = new ExecReportResponse(canceledOrder, ExecType.REMOVE);
            TransactionComplete transactionComplete = new TransactionComplete();
            reports.add(response);
            reports.add(transactionComplete);
            log().info("Distributing Execution Report: " + response.toString() + " To " + subscribers.size() + " Subscribers");

            if (nonSubscribers.containsKey(request.getTraderID())) {
                trader.tell(response, getSelf());
                trader.tell(transactionComplete, getSelf());
                tryRemovingTrader(request.getTraderID());
            }
            notifySubscribers(response);
            notifySubscribers(transactionComplete);
        } else {
            log().info("Cancel Order Request Rejection Reason: " + rejectionReason.name());
            response = new ExecReportResponse(request, rejectionReason);
            trader.tell(response, getSelf());
        }

    }

    private void tryRemovingTrader(int traderID) {
        if (hasNoOrders(traderID)) {
            context().unwatch(nonSubscribers.get(traderID));
            nonSubscribers.remove(traderID);
        }
    }

    private boolean hasNoOrders(int traderID) {
        for (int orderID : orders.keySet()) {
            if (orders.get(orderID).getTraderID() == traderID) {
                return false;
            }
        }
        return true;
    }

    private RejectionReason cancelOrderRequestRejectionReason(CancelOrderRequest request, ActorRef trader) {

        if (!subscribers.containsKey(request.getTraderID()) && !nonSubscribers.containsKey(request.getTraderID())) {
            return RejectionReason.INVALID_TRADER_ID;
        }

        if (subscribers.containsKey(request.getTraderID()) && !subscribers.get(request.getTraderID()).equals(trader)) {
            return RejectionReason.INVALID_TRADER_ID;
        }

        if (nonSubscribers.containsKey(request.getTraderID()) && !nonSubscribers.get(request.getTraderID()).equals(trader)) {
            return RejectionReason.INVALID_TRADER_ID;
        }

        if (!orders.containsKey(request.getOrderID()) || orders.get(request.getOrderID()).getTraderID() != request.getTraderID()) {
            return RejectionReason.INVALID_ORDER_ID;
        }
        return null;
    }

    private void onSubscriptionRequest(ActorRef trader, SubscriptionRequest request) {

        log().info("Subscription Request Received From: " + trader);
        subscribers.put(request.getTraderID(), trader);

        if (nonSubscribers.containsKey(request.getTraderID())) {
            nonSubscribers.remove(request.getTraderID());
        }

        context().watch(trader);
        sendReportsToTrader(trader);
    }

    private void sendReportsToTrader(ActorRef trader) {
        for (Response response : reports) {
            trader.tell(response, getSelf());
        }
        trader.tell(new SubscriptionResponse(), getSelf());
    }

    private void onTerminationMessage(Terminated terminated) {
        int traderID = retrieveTraderID(terminated.actor());
        for (int orderID : orders.keySet()) {
            Order order = orders.get(orderID);
            if (order.getTraderID() == traderID) {
                orderBooks.get(order.getInstrument()).cancelOrder(order.getOrderID());
                ExecReportResponse response = new ExecReportResponse(order, ExecType.REMOVE);
                reports.add(response);
                reports.add(new TransactionComplete());
            }
        }
    }

    private int retrieveTraderID(ActorRef trader) {
        for (int traderID : subscribers.keySet()) {
            if (subscribers.get(traderID) == trader) {
                return traderID;
            }
        }
        for (int traderID : nonSubscribers.keySet()) {
            if (nonSubscribers.get(traderID) == trader) {
                return traderID;
            }
        }
        return -1;
    }

    //if the state of order books is changed, all the traders get notified
    private void notifySubscribers(Response response) {
        for (int traderID : subscribers.keySet()) {
            subscribers.get(traderID).tell(response, getSelf());
        }
    }

    //tries to match the best orders
    private void matchOrders(Instrument instrument) {
        while (true) {
            //at the beginning, there might be no best orders in any of sides
            if (orderBooks.get(instrument).getBids().size() == 0 || orderBooks.get(instrument).getOffers().size() == 0) {
                break;
            }
            Order bid = orderBooks.get(instrument).getBids().get(0);
            Order offer = orderBooks.get(instrument).getOffers().get(0);

            log().info("Matching " + bid.toString());
            log().info("And " + offer.toString());

            if (checkOrderMatching(bid, offer)) {
                executeTransaction(bid, offer);
            } else {
                break;
            }
        }

        TransactionComplete transactionComplete = new TransactionComplete();
        reports.add(transactionComplete);
        notifySubscribers(transactionComplete);
        tellTCToNonSubscribers();
        return;
    }

    // checks whether the two orders match
    private boolean checkOrderMatching(Order bid, Order offer) {
        if (bid.getPrice() >= offer.getPrice()) {
            return true;
        }
        return false;
    }

    //if two orders match, transaction is being executed
    private void executeTransaction(Order bid, Order offer) {

        int tradeQuantity = min(bid.getQuantity(), offer.getQuantity());
        double tradePrice = getTradePrice(bid, offer);
        TradeMessage tradeMessage = new TradeMessage(bid.getOrderID(), offer.getOrderID(), tradePrice, tradeQuantity, bid.getInstrument());
        reports.add(tradeMessage);
        ExecReportResponse bidResponse = updateMatchedOrder(bid, tradeQuantity);
        ExecReportResponse offerResponse = updateMatchedOrder(offer, tradeQuantity);

        tellTradeResponseToNonSubscriber(bidResponse);
        tellTradeResponseToNonSubscriber(offerResponse);
        notifySubscribers(bidResponse);
        notifySubscribers(offerResponse);

        if (nonSubscribers.containsKey(bidResponse.getTraderID())) {
            nonSubscribers.get(bidResponse.getTraderID()).tell(tradeMessage, getSelf());
            if (bidResponse.getExecType() == ExecType.REMOVE) {
                tryRemovingTrader(bidResponse.getTraderID());
            }
        }
        if (nonSubscribers.containsKey(offerResponse.getTraderID())) {
            nonSubscribers.get(offerResponse.getTraderID()).tell(tradeMessage, getSelf());
            if (offerResponse.getExecType() == ExecType.REMOVE) {
                tryRemovingTrader(offerResponse.getTraderID());
            }
        }
        notifySubscribers(tradeMessage);

        log().info("Trade Completed: " + tradeMessage.toString());
    }

    private void tellTradeResponseToNonSubscriber(ExecReportResponse response) {
        if (nonSubscribers.containsKey(response.getTraderID())) {
            if (!nonSubscribersTonotify.contains(nonSubscribers.get(response.getTraderID()))) {
                nonSubscribersTonotify.add(nonSubscribers.get(response.getTraderID()));
            }
            nonSubscribers.get(response.getTraderID()).tell(response, getSelf());
        }
    }

    //sets the trade price according to the state of orders
    private double getTradePrice(Order bid, Order offer) {
        if (bid.getPrice() == offer.getPrice()) {
            return bid.getPrice();
        }

        if (offer.getOrderID() > bid.getOrderID()) {
            return bid.getPrice();
        } else {
            return offer.getPrice();
        }
    }

    private ExecReportResponse updateMatchedOrder(Order order, int tradeQuantity) {
        if (order.getQuantity() > tradeQuantity) {
            orderBooks.get(order.getInstrument()).partFillOrder(order.getOrderID(), order.getQuantity() - tradeQuantity);
            ExecReportResponse response = new ExecReportResponse(order, ExecType.UPDATE);
            reports.add(response);
            return response;
        } else {
            orderBooks.get(order.getInstrument()).fullyExecuteOrder(order.getOrderID());
            ExecReportResponse response = new ExecReportResponse(order, ExecType.REMOVE);
            reports.add(response);
            orders.remove(order.getOrderID());
            return response;
        }
    }

    private void tellTCToNonSubscribers() {
        for (ActorRef trader : nonSubscribersTonotify) {
            trader.tell(new TransactionComplete(), getSelf());
        }
        nonSubscribersTonotify.clear();
    }

}
