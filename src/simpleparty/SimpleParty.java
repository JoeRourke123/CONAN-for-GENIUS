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

        System.out.println(timeline.getTime());
        System.out.println(timeline.getCurrentTime());
        System.out.println(timeline.getTotalTime());
        System.out.println("---------");

        // Checks that the agent has received an Offer and not some other form of message
        if(lastReceived instanceof Offer) {
            lastBid = ((Offer) lastReceived).getBid();

            // Accept the bid if it's acceptable (i.e. has a high enough utility)
            if(getUtility(lastBid) >= UTIL_THRESHOLD) {
                return new Accept(getPartyId(), lastBid);
            }
        }

        // Otherwise, return a randomly generated bid
        Bid nextBid = generateRandomBid();

        // Not sure this is the best method of doing so
        // but this is a simple fix to only generate high utility bids which marginally improves bid utility
        while(getUtility(nextBid) < UTIL_THRESHOLD) {
            nextBid = generateRandomBid();
        }

        // Send this offer to the other parties
        return new Offer(getPartyId(), nextBid);
    }

    @Override
    public String getDescription() {
        return "My first simple agent";
    }
}
