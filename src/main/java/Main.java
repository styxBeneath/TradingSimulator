
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.util.Date;

public class Main {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create();
        int numOfTraderID = 0;
        final ActorRef engine = system.actorOf(MatchEngine.props(), "engine");
        final ActorRef trader1 = system.actorOf(Trader.props(engine, numOfTraderID++), "trader1");
        final ActorRef trader2 = system.actorOf(Trader.props(engine, numOfTraderID++), "trader2");
        final ActorRef trader3 = system.actorOf(Trader.props(engine, numOfTraderID++), "trader3");
    }
}
