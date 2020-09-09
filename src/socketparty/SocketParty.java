package socketparty;

import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.NegotiationInfo;
import z3enius.Z3niusParty;

import java.util.List;

public class SocketParty extends Z3niusParty {
    //    private Process process;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        // This code will automatically launch the Z3GENIUS program if uncommented
//        try {
//            process = new ProcessBuilder("java", "-jar", "artifacts/Z3GENIUS_jar/Z3GENIUS.jar").start();
//        } catch (IOException i) {
//            System.err.println("IOException occurred");
//        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        Action lastReceived = getLastReceivedAction();
        Bid lastBid;

        // Checks that the agent has received an Offer and not some other form of message
        if (lastReceived instanceof Offer) {
            lastBid = ((Offer) lastReceived).getBid();

            // Accept the bid if it's acceptable (i.e. has a high enough utility)
            if (getUtility(lastBid) >= 0.8) {
                return new Accept(getPartyId(), lastBid);
            }
        }

        // Otherwise, return a randomly generated bid
        Bid nextBid = generateRandomBid();

        // Not sure this is the best method of doing so
        // but this is a simple fix to only generate high utility bids which marginally improves bid utility
        while (getUtility(nextBid) < 0.8) {
            nextBid = generateRandomBid();
        }
        // Send this offer to the other parties
        return new Offer(getPartyId(), nextBid);
    }

    @Override
    public String getDescription() {
        return "Connects to Z3 solver over a socket connection";
    }
}
