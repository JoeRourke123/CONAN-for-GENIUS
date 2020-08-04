package simpleparty;

import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;

import java.util.List;

public class SimpleParty extends AbstractNegotiationParty {
    private final double UTIL_THRESHOLD = 0.85;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        Action lastReceived = getLastReceivedAction();
        Bid lastBid;


        if(lastReceived instanceof Offer) {
            lastBid = ((Offer) lastReceived).getBid();

            if(getUtility(lastBid) >= UTIL_THRESHOLD) {
                return new Accept(getPartyId(), lastBid);
            }
        }
        Bid nextBid = generateRandomBid();

        while(getUtility(nextBid) < UTIL_THRESHOLD) {
            nextBid = generateRandomBid();
        }

        return new Offer(getPartyId(), nextBid);
    }

    @Override
    public String getDescription() {
        return "My first simple agent";
    }
}
