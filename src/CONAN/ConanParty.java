package CONAN;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.*;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.EvaluatorInteger;
import z3enius.Z3niusParty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConanParty extends Z3niusParty {
    private double[] concessions;
    HashMap<AgentID, List<Bid>> agentBids;
    HashMap<AgentID, Double> agentResponseTimes;

    private boolean hasEnded = false;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        if(!hasPreferenceUncertainty()) {
            weightings = new double[getIssuesSize()];
            for(int i = 0; i < getIssuesSize(); i++) {
                weightings[i] = ((AdditiveUtilitySpace) info.getUtilitySpace()).getWeight(i + 1);
            }
        }

        agentBids = new HashMap<>();
        agentResponseTimes = new HashMap<>();
    }

    @Override
    public void receiveMessage(AgentID sender, Action act) {
        if (!hasEnded) {
            if (act instanceof Offer && !sender.equals(getPartyId())) {
                if (!agentBids.containsKey(sender)) {
                    agentBids.put(sender, new ArrayList<>());
                }
                agentResponseTimes.put(sender, timeline.getTime());
                agentBids.get(sender).add(((Offer) act).getBid());
            }
        }

        super.receiveMessage(sender, act);
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        Bid currentBid = getNextBid();
        printBid(currentBid, "Generated Bid");

        double currentBidUtility = getUtility(currentBid);

        double receivedUtility;
        for (Bid lastReceived : getLastBids()) {
            receivedUtility = getUtility(lastReceived);

            if (receivedUtility >= currentBidUtility) {
                hasEnded = true;
                printBid(lastReceived, "Accepting Bid");
                return new Accept(getPartyId(), lastReceived);
            }
        }

        return new Offer(getPartyId(), currentBid);
    }

    @Override
    public String getDescription() {
        return "Implementation of the ConanParty strategy for GENIUS";
    }

    @Override
    public HashMap<String, String> negotiationEnded(Bid acceptedBid) {
        return super.negotiationEnded(acceptedBid);
    }

    private Bid getNextBid() {
        generateConcessions();

        printConcessions();

        HashMap<Integer, Value> mappedIssueValues = new HashMap<>();

        for (int i = 0; i < getIssuesSize(); i++) {
            if (getIssue(i).getType() == ISSUETYPE.DISCRETE) {
                IssueDiscrete disIssue = (IssueDiscrete) getIssue(i);

                EvaluatorDiscrete disEval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(disIssue);

                ValueDiscrete pickedValue = CONANUtils.getDiscreteValue(disIssue, disEval, concessions[i]);
                mappedIssueValues.put(i + 1, pickedValue);
            } else if (getIssue(i).getType() == ISSUETYPE.INTEGER) {
                IssueInteger intIssue = (IssueInteger) getIssue(i);

                ValueInteger calculatedValue = CONANUtils.getIntegerValue(intIssue.getUpperBound(), intIssue.getLowerBound(), concessions[i]);
                mappedIssueValues.put(i + 1, calculatedValue);
            }
        }

        return new Bid(getDomain(), mappedIssueValues);
    }

    private void generateConcessions() {
        double environment = CONANUtils.getEnvironmentFactor();
        double[] newConcessions = new double[getIssuesSize()];

        for (int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            if (timeline.getTime() >= 1 - (1.0 / (1.0 * timeline.getTotalTime()))) {
                newConcessions[issueIndex] = 0.99;
            } else if (timeline.getTime() > (1.0 / (1.0 * timeline.getTotalTime()))) {
                double self = CONANUtils.getSelfFactor(getIssue(issueIndex), this);

                double selfWeight = CONANUtils.getSelfWeighting(
                        ((AdditiveUtilitySpace) utilitySpace).getEvaluator(getIssue(issueIndex)),
                        getIssue(issueIndex), self, timeline.getTime());

                double newConcession = (selfWeight * self) + ((1 - selfWeight) * environment);

                if (newConcession - concessions[issueIndex] >= 0) {
                    newConcessions[issueIndex] = newConcession;
                } else {
                    newConcessions[issueIndex] = concessions[issueIndex];
                }
            }
        }

        concessions = newConcessions;
    }

    private Issue getIssue(int i) {
        return getDomain().getIssues().get(i);
    }

    private int getIssuesSize() {
        return getDomain().getIssues().size();
    }

    private List<Bid> getLastBids() {
        List<Bid> bids = new ArrayList<>();
        for (AgentID agent : agentBids.keySet()) {
            bids.add(agentBids.get(agent).get(agentBids.get(agent).size() - 1));
        }
        return bids;
    }

    private void printConcessions() {
        printLine();
        System.out.println("Concessions\n--------");
        for (int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            System.out.println(getIssue(issueIndex).getName() + ": " + concessions[issueIndex]);
        }
    }

    private void printBid(Bid b, String title) {
        printLine();
        if (title != null) {
            System.out.println(title);
            System.out.println("--------");
        }
        for (int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            Issue iss = getIssue(issueIndex);

            if (iss.getType() == ISSUETYPE.DISCRETE) {
                System.out.println(iss.getName() + ": " + ((ValueDiscrete) b.getValue(iss)).getValue());
            } else {
                System.out.println(iss.getName() + ": " + ((ValueInteger) b.getValue(iss)).getValue());
            }
        }
        System.out.println("--------");
        System.out.println("Util: " + getUtility(b));
    }

    private void printLine() {
        System.out.println("============");
    }

    @Override
    public double getUtility(Bid bid) {
        double util = 0.0;
        for(int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            Issue i = getIssue(issueIndex);

            if(i.getType() == ISSUETYPE.DISCRETE) {
                EvaluatorDiscrete eval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(i);
                eval.scaleAllValuesFrom0To1();
                util += weightings[issueIndex] * (eval.getDoubleValue((ValueDiscrete) bid.getValue(i)) / eval.getEvalMax());
            } else if(i.getType() == ISSUETYPE.INTEGER) {
                EvaluatorInteger eval = (EvaluatorInteger) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(i);
                util += weightings[issueIndex] * ((eval.getSlope() * ((ValueInteger) bid.getValue(i)).getValue()) + eval.getOffset());
            }
        }
        return Math.min(util, 1.0);
    }
}
