import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import enums.*;
import messages.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;

public class TestME {
    static ActorSystem system;

    @BeforeAll
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterAll
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testSubscriptionWithoutLiveOrders() {

        new TestKit(system) {
            {

                final ActorRef matchEngine = system.actorOf(MatchEngine.props());
                matchEngine.tell(new SubscriptionRequest(1), getRef());
                expectMsgClass(SubscriptionResponse.class);
            }

        };
    }

    @Test
    public void testSuccessfulRequests() {
        new TestKit(system) {
            {
                final ActorRef matchEngine = system.actorOf(MatchEngine.props());

                NewOrderRequest request = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
                matchEngine.tell(request, getRef());
                ExecReportResponse response = expectMsgClass(ExecReportResponse.class);
                expectMsgClass(TransactionComplete.class);

                Assertions.assertEquals(request.getPrice(), response.getPrice(), "invalid price");
                Assertions.assertEquals(request.getQuantity(), response.getQuantity(), "invalid quantity");
                Assertions.assertEquals(request.getTraderID(), response.getTraderID(), "invalid trader ID");
                Assertions.assertEquals(Status.ACTIVE, response.getStatus(), "invalid status");
                Assertions.assertEquals(request.getSide(), response.getSide(), "invalid side");
                Assertions.assertEquals(request.getInstrument(), response.getInstrument(), "invalid instrument");

                ModifyOrderRequest request1 = new ModifyOrderRequest(response.getOrderID(), 1, 4, 3);
                matchEngine.tell(request1, getRef());
                ExecReportResponse response1 = expectMsgClass(ExecReportResponse.class);
                expectMsgClass(TransactionComplete.class);

                Assertions.assertEquals(request1.getPrice(), response1.getPrice(), "invalid price");
                Assertions.assertEquals(request1.getQuantity(), response1.getQuantity(), "invalid quantity");
                Assertions.assertEquals(request1.getTraderID(), response1.getTraderID(), "invalid trader ID");
                Assertions.assertEquals(request1.getOrderID(), response1.getOrderID(), "invalid order ID");

                CancelOrderRequest request2 = new CancelOrderRequest(response1.getOrderID(), 1);
                matchEngine.tell(request2, getRef());
                ExecReportResponse response2 = expectMsgClass(ExecReportResponse.class);
                expectMsgClass(TransactionComplete.class);

                Assertions.assertEquals(request2.getTraderID(), response2.getTraderID(), "invalid trader ID");
                Assertions.assertEquals(request2.getOrderID(), response2.getOrderID(), "invalid order ID");
                Assertions.assertEquals(Status.CANCELED, response2.getStatus(), "invalid status");
            }
        };
    }

    @Test
    public void testNORWithInvalidQuantity() {

        new TestKit(system) {
            {
                final ActorRef matchEngine = system.actorOf(MatchEngine.props());

                NewOrderRequest request = new NewOrderRequest(Side.BIDS, Instrument.BAG, 0, 2, new Date(), 1);
                matchEngine.tell(request, getRef());
                ExecReportResponse response = expectMsgClass(ExecReportResponse.class);
                Assertions.assertEquals(RejectionReason.INVALID_QUANTITY, response.getRejectionReason());

            }
        };

    }

    @Test
    public void testNORWithInvalidPrice() {
        new TestKit(system) {
            {
                final ActorRef matchEngine = system.actorOf(MatchEngine.props());

                NewOrderRequest request1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 0, new Date(), 1);
                matchEngine.tell(request1, getRef());
                ExecReportResponse response1 = expectMsgClass(ExecReportResponse.class);
                Assertions.assertEquals(RejectionReason.INVALID_PRICE, response1.getRejectionReason());

            }
        };
    }

    @Test
    public void testMORWithInvalidQuantity() {
        new TestKit(system) {
            {
                final ActorRef matchEngine = system.actorOf(MatchEngine.props());

                //adding new order in ME, further to be modified
                NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
                matchEngine.tell(newOrderRequest, getRef());
                ExecReportResponse response = expectMsgClass(ExecReportResponse.class);
                expectMsgClass(TransactionComplete.class);
                Assertions.assertEquals(Status.ACTIVE, response.getStatus());

                //case of invalid quantity
                ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(response.getOrderID(), 1, -1, 3);
                matchEngine.tell(modifyOrderRequest, getRef());
                ExecReportResponse response1 = expectMsgClass(ExecReportResponse.class);
                Assertions.assertEquals(RejectionReason.INVALID_QUANTITY, response1.getRejectionReason());
            }
        };
    }

    @Test
    public void testMORWithInvalidPrice() {
        new TestKit(system) {
            {
                final ActorRef matchEngine = system.actorOf(MatchEngine.props());

                //adding new order in ME, further to be modified
                NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
                matchEngine.tell(newOrderRequest, getRef());
                ExecReportResponse response = expectMsgClass(ExecReportResponse.class);
                expectMsgClass(TransactionComplete.class);
                Assertions.assertEquals(Status.ACTIVE, response.getStatus());

                //case of invalid price
                ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(response.getOrderID(), 1, 1, -1);
                matchEngine.tell(modifyOrderRequest, getRef());
                ExecReportResponse response1 = expectMsgClass(ExecReportResponse.class);
                Assertions.assertEquals(RejectionReason.INVALID_PRICE, response1.getRejectionReason());
            }
        };
    }

    @Test
    public void testMORWithFirstTimeUser() {
        //testing MOR when trader hasn't sent any request yet

        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest, trader1.getRef());
        ExecReportResponse NORResponse = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //sending MOR from trader2
        ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(NORResponse.getOrderID(), 2, 1, 1);
        matchEngine.tell(modifyOrderRequest, trader2.getRef());
        ExecReportResponse MORResponse = trader2.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_TRADER_ID, MORResponse.getRejectionReason());
    }

    @Test
    public void testMORWithDifferentTraderID() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest1, trader1.getRef());
        ExecReportResponse NORResponse1 = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //adding new order for trader2
        NewOrderRequest newOrderRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 3, 3, new Date(), 2);
        matchEngine.tell(newOrderRequest2, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);

        //sending MOR from trader2 with trader1's trader ID
        ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(NORResponse1.getOrderID(), 1, 1, 1);
        matchEngine.tell(modifyOrderRequest, trader2.getRef());
        ExecReportResponse MORResponse = trader2.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_TRADER_ID, MORResponse.getRejectionReason());
    }

    @Test
    public void testMORWithInvalidOrderID() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest1, trader1.getRef());
        ExecReportResponse NORResponse1 = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //adding new order for trader2
        NewOrderRequest newOrderRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 3, 3, new Date(), 2);
        matchEngine.tell(newOrderRequest2, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);

        //sending MOR from trader2 on trade1's order
        ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(NORResponse1.getOrderID(), 2, 1, 1);
        matchEngine.tell(modifyOrderRequest, trader2.getRef());
        ExecReportResponse MORResponse = trader2.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_ORDER_ID, MORResponse.getRejectionReason());
    }

    @Test
    public void testMORWithNonExistentOrderID() {
        final TestKit trader = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader, orderID for this order will be 0
        NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest, trader.getRef());
        trader.expectMsgClass(ExecReportResponse.class);
        trader.expectMsgClass(TransactionComplete.class);

        // sending MOR with non-existent orderID
        ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(10, 1, 1, 1);
        matchEngine.tell(modifyOrderRequest, trader.getRef());
        ExecReportResponse MORResponse = trader.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_ORDER_ID, MORResponse.getRejectionReason());
    }

    @Test
    public void testCORWithFirstTimeUser() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest, trader1.getRef());
        ExecReportResponse NORResponse = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //sending COR from trader2
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(NORResponse.getOrderID(), 2);
        matchEngine.tell(cancelOrderRequest, trader2.getRef());
        ExecReportResponse CORResponse = trader2.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_TRADER_ID, CORResponse.getRejectionReason());
    }

    @Test
    public void testCORWithDifferentTraderID() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest1, trader1.getRef());
        ExecReportResponse NORResponse1 = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //adding new order for trader2
        NewOrderRequest newOrderRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 3, 3, new Date(), 2);
        matchEngine.tell(newOrderRequest2, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);

        //sending COR from trader2 with trader1's trader ID
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(NORResponse1.getOrderID(), 1);
        matchEngine.tell(cancelOrderRequest, trader2.getRef());
        ExecReportResponse CORResponse = trader2.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_TRADER_ID, CORResponse.getRejectionReason());
    }

    @Test
    public void testCORWithInvalidOrderID() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest1, trader1.getRef());
        ExecReportResponse NORResponse1 = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //adding new order for trader2
        NewOrderRequest newOrderRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 3, 3, new Date(), 2);
        matchEngine.tell(newOrderRequest2, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);

        //sending COR from trader2 on trade1's order
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(NORResponse1.getOrderID(), 2);
        matchEngine.tell(cancelOrderRequest, trader2.getRef());
        ExecReportResponse CORResponse = trader2.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_ORDER_ID, CORResponse.getRejectionReason());
    }

    @Test
    public void testCORWithNonExistentOrderID() {
        final TestKit trader = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader, orderID for this order will be 0
        NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest, trader.getRef());
        trader.expectMsgClass(ExecReportResponse.class);
        trader.expectMsgClass(TransactionComplete.class);

        // sending COR with non-existent orderID
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(10, 1);
        matchEngine.tell(cancelOrderRequest, trader.getRef());
        ExecReportResponse CORResponse = trader.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_ORDER_ID, CORResponse.getRejectionReason());
    }

    @Test
    public void testSubscriptionWithLiveOrders() {
        final TestKit trader1 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        //adding new order for trader1
        NewOrderRequest newOrderRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        matchEngine.tell(newOrderRequest, trader1.getRef());
        ExecReportResponse NORResponse = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //modifying the order of trader1
        ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(NORResponse.getOrderID(), 1, 3, 3);
        matchEngine.tell(modifyOrderRequest, trader1.getRef());
        ExecReportResponse MORResponse = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //cancelling the order of trader1
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(NORResponse.getOrderID(), 1);
        matchEngine.tell(cancelOrderRequest, trader1.getRef());
        ExecReportResponse CORResponse = trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        final TestKit trader2 = new TestKit(system);
        matchEngine.tell(new SubscriptionRequest(2), trader2.getRef());

        ExecReportResponse response1 = trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);
        ExecReportResponse response2 = trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);
        ExecReportResponse response3 = trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);
        trader2.expectMsgClass(SubscriptionResponse.class);

        //ER's received by trader1 and trader2 must be the same
        Assertions.assertEquals(NORResponse, response1);
        Assertions.assertEquals(MORResponse, response2);
        Assertions.assertEquals(CORResponse, response3);
    }

    @Test
    public void testTradeBidAndOffer() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());


        NewOrderRequest NORRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest NORRequest2 = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 2, 2, new Date(), 2);

        matchEngine.tell(NORRequest1, trader1.getRef());
        // trader1 should receive ER and TC
        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(NORRequest2, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);


        ExecReportResponse response1 = trader1.expectMsgClass(ExecReportResponse.class); // order fully executed
        trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        ExecReportResponse response2 = trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        Assertions.assertEquals(Status.FULLY_EXECUTED, response1.getStatus());
        Assertions.assertEquals(Status.FULLY_EXECUTED, response2.getStatus());


    }

    @Test
    public void testTradeMultipleBidsAndOffer() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final TestKit trader3 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());


        NewOrderRequest NORRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest NORRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 3, 2, new Date(), 2);
        NewOrderRequest NORRequest3 = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 5, 2, new Date(), 3);

        matchEngine.tell(NORRequest1, trader1.getRef());
        //all traders should receive ER and TC
        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(NORRequest2, trader2.getRef());
        //all traders should receive ER and TC

        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(NORRequest3, trader3.getRef());

        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(TradeMessage.class);
        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(TradeMessage.class);
        trader3.expectMsgClass(TransactionComplete.class);

        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        trader1.expectNoMessage();
        trader2.expectNoMessage();
        trader3.expectNoMessage();

    }

    @Test
    public void testTradeMultipleBidsAndOfferPlusModify() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final TestKit trader3 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());


        NewOrderRequest NORRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest NORRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 3, 2, new Date(), 2);
        NewOrderRequest NORRequest3 = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 5, 6, new Date(), 3);

        matchEngine.tell(NORRequest1, trader1.getRef());
        //all traders should receive ER and TC
        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(NORRequest2, trader2.getRef());
        //all traders should receive ER and TC

        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(NORRequest3, trader3.getRef());

        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(TransactionComplete.class);

        ModifyOrderRequest modifyOrderRequest = new ModifyOrderRequest(2, 3, 5, 2);
        matchEngine.tell(modifyOrderRequest, trader3.getRef());
        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(TradeMessage.class);
        trader3.expectMsgClass(ExecReportResponse.class);
        trader3.expectMsgClass(TradeMessage.class);
        trader3.expectMsgClass(TransactionComplete.class);

        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        trader1.expectNoMessage();
        trader2.expectNoMessage();
        trader3.expectNoMessage();

    }

    @Test
    public void testMessagesReceivedBySubscriberAfterTransaction() {
        final TestKit subscriber = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        matchEngine.tell(new SubscriptionRequest(0), subscriber.getRef());
        subscriber.expectMsgClass(SubscriptionResponse.class);

        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);

        NewOrderRequest request1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest request2 = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 2, 2, new Date(), 2);

        matchEngine.tell(request1, trader1.getRef());

        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(request2, trader2.getRef());

        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(TradeMessage.class);
        subscriber.expectMsgClass(TransactionComplete.class);
    }

    @Test
    public void testTradePriceWithPassiveBid() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());


        NewOrderRequest bidRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 3, new Date(), 1);
        NewOrderRequest offerRequest = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 2, 2, new Date(), 2);

        matchEngine.tell(bidRequest, trader1.getRef());
        // trader1 should receive ER and TC
        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(offerRequest, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);

        trader1.expectMsgClass(ExecReportResponse.class);
        TradeMessage tradeMessage1 = trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        trader2.expectMsgClass(ExecReportResponse.class);
        TradeMessage tradeMessage2 = trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        Assertions.assertEquals(tradeMessage1, tradeMessage2);
        Assertions.assertEquals(bidRequest.getPrice(), tradeMessage1.getTradePrice());
    }

    @Test
    public void testTradePriceWithPassiveOffer() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());


        NewOrderRequest offerRequest = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest bidRequest = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 3, new Date(), 2);

        matchEngine.tell(offerRequest, trader1.getRef());
        // trader1 should receive ER and TC
        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(bidRequest, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);

        trader1.expectMsgClass(ExecReportResponse.class);
        TradeMessage tradeMessage1 = trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        trader2.expectMsgClass(ExecReportResponse.class);
        TradeMessage tradeMessage2 = trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        Assertions.assertEquals(tradeMessage1, tradeMessage2);
        Assertions.assertEquals(offerRequest.getPrice(), tradeMessage1.getTradePrice());
    }

    @Test
    public void testCancellingOrderWhenItIsBeingExecuted() {
        final TestKit trader1 = new TestKit(system);
        final TestKit trader2 = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());


        NewOrderRequest NORRequest1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest NORRequest2 = new NewOrderRequest(Side.BIDS, Instrument.BOOK, 2, 2, new Date(), 1);
        NewOrderRequest NORRequest3 = new NewOrderRequest(Side.OFFERS, Instrument.BAG, 2, 2, new Date(), 2);

        matchEngine.tell(NORRequest1, trader1.getRef());
        // trader1 should receive ER and TC
        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        //purpose of this order is to keep trader1 in ME's active traders after NORRequest1 is fully executed
        matchEngine.tell(NORRequest2, trader1.getRef());

        trader1.expectMsgClass(ExecReportResponse.class);
        trader1.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(NORRequest3, trader2.getRef());
        trader2.expectMsgClass(ExecReportResponse.class);

        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(0, 1);
        matchEngine.tell(cancelOrderRequest, trader1.getRef());

        ExecReportResponse response1 = trader1.expectMsgClass(ExecReportResponse.class); // order fully executed
        trader1.expectMsgClass(TradeMessage.class);
        trader1.expectMsgClass(TransactionComplete.class);

        ExecReportResponse response2 = trader2.expectMsgClass(ExecReportResponse.class);
        trader2.expectMsgClass(TradeMessage.class);
        trader2.expectMsgClass(TransactionComplete.class);

        Assertions.assertEquals(Status.FULLY_EXECUTED, response1.getStatus());
        Assertions.assertEquals(Status.FULLY_EXECUTED, response2.getStatus());

        ExecReportResponse response3 = trader1.expectMsgClass(ExecReportResponse.class);
        Assertions.assertEquals(RejectionReason.INVALID_ORDER_ID, response3.getRejectionReason());
    }

    @Test
    public void testTraderTermination() {
        final TestKit subscriber = new TestKit(system);
        final TestKit nonSubscriber = new TestKit(system);
        final ActorRef matchEngine = system.actorOf(MatchEngine.props());

        NewOrderRequest request1 = new NewOrderRequest(Side.BIDS, Instrument.BAG, 2, 2, new Date(), 1);
        NewOrderRequest request2 = new NewOrderRequest(Side.BIDS, Instrument.BOOK, 2, 2, new Date(), 1);

        matchEngine.tell(request1, nonSubscriber.getRef());
        nonSubscriber.expectMsgClass(ExecReportResponse.class);
        nonSubscriber.expectMsgClass(TransactionComplete.class);

        matchEngine.tell(request2, nonSubscriber.getRef());
        nonSubscriber.expectMsgClass(ExecReportResponse.class);
        nonSubscriber.expectMsgClass(TransactionComplete.class);

        system.stop(nonSubscriber.getTestActor());

        matchEngine.tell(new SubscriptionRequest(2), subscriber.getRef());
        subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(TransactionComplete.class);
        subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(TransactionComplete.class);

        ExecReportResponse response1 = subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(TransactionComplete.class);
        ExecReportResponse response2 = subscriber.expectMsgClass(ExecReportResponse.class);
        subscriber.expectMsgClass(TransactionComplete.class);

        subscriber.expectMsgClass(SubscriptionResponse.class);
        subscriber.expectNoMessage();

        Assertions.assertEquals(ExecType.REMOVE, response1.getExecType());
        Assertions.assertEquals(ExecType.REMOVE, response2.getExecType());
        Assertions.assertTrue(nonSubscriber.getTestActor().isTerminated());

    }
}
