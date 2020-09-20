package CONAN;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.*;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.EvaluatorInteger;
import z3enius.Z3niusParty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// The party extends Z3nius to take advantage of the utility space estimation capabilities.
public class ConanParty extends Z3niusParty {
    // The concession rate for each issue is stored in this array
    private double[] concessions;

    // These structures store values which are used in the bid and concession calculations, helping gauge the
    // compatibility of each opponent agent
    HashMap<AgentID, List<Bid>> agentBids;
    HashMap<AgentID, Double> agentResponseTimes;

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

    /**
     * Whenever an offer is received, it is stored in the opponent's list of bids and the time they responded is noted.
     * Then the super method from the AbstractNegotiationParty class is called to handle other aspects of the scenario
     * @param sender - the agent who sent the action
     * @param act - the action which has been sent (could be an offer, acceptance, or exit from negotiation)
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        if (act instanceof Offer && !sender.equals(getPartyId())) {
            if (!agentBids.containsKey(sender)) {
                agentBids.put(sender, new ArrayList<>());
            }
            agentResponseTimes.put(sender, timeline.getTime());
            agentBids.get(sender).add(((Offer) act).getBid());
        }

        super.receiveMessage(sender, act);
    }

    /**
     * This is the GENIUS function which allows the agent to decide on what action to take next depending on the current
     * situation in the negotiation. I use the CONAN heuristics here, comparing the received offer to the next generated
     * bid - accepting if the utility of the offer is above that of the generated bid. Otherwise, this bid is sent out
     * to the opponents.
     * @param possibleActions - which values can be chosen in any given scenario
     * @return - an action object, which is usually a subclass of this (Accept, Offer, etc)
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        Bid currentBid = getNextBid();
        printBid(currentBid, "Generated Bid");

        double currentBidUtility = getUtility(currentBid);

        double receivedUtility = -1.0;
        Bid acceptedBid = null;
        for (Bid lastReceived : getLastBids()) {
            if (receivedUtility >= currentBidUtility) {
                printBid(lastReceived, "Accepting Bid");
                receivedUtility = getUtility(lastReceived);
                acceptedBid = lastReceived;
            }
        }
        // If any of the searched bids are compatible, return an acceptance for said bid
        if(acceptedBid != null) {
            return new Accept(getPartyId(), acceptedBid);
        }


        // If there are no bids to accept, return an offer with the generated bid for the agent
        return new Offer(getPartyId(), currentBid);
    }

    @Override
    public String getDescription() {
        return "Implementation of the ConanParty strategy for GENIUS";
    }

    /**
     * This method generates a bid object using the CONAN heuristics.
     * @return - a Bid object which is then used in the chooseAction method
     */
    private Bid getNextBid() {
        // Generates the concession rates to be used in the newly generated issue values
        generateConcessions();

        // These concessions are printed in the console, for debugging purposes
        printConcessions();

        // This map is passed into the bid object on its instantiation
        HashMap<Integer, Value> mappedIssueValues = new HashMap<>();

        for (int i = 0; i < getIssuesSize(); i++) {
            if (getIssue(i).getType() == ISSUETYPE.DISCRETE) {
                // Use the CONANUtils static method to generate a bid value for this issue, using the agent's evaluator
                // object and the heuristic concession value
                IssueDiscrete disIssue = (IssueDiscrete) getIssue(i);

                EvaluatorDiscrete disEval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(disIssue);

                ValueDiscrete pickedValue = CONANUtils.getDiscreteValue(disIssue, disEval, concessions[i]);
                mappedIssueValues.put(i + 1, pickedValue);      // Enter this generated value into the map
            } else if (getIssue(i).getType() == ISSUETYPE.INTEGER) {
                IssueInteger intIssue = (IssueInteger) getIssue(i);

                // The calculated value is generated using the upper and lower bounds of the possible values in the
                // continuous issue
                ValueInteger calculatedValue = CONANUtils.getIntegerValue(intIssue.getUpperBound(), intIssue.getLowerBound(), concessions[i]);
                mappedIssueValues.put(i + 1, calculatedValue);
            }
        }

        // The bid is returned with the generated values entered into it
        return new Bid(getDomain(), mappedIssueValues);
    }

    /**
     * Populates the concessions array with the current calculated concession values at the given point in the
     * negotiation scenario. The same constraints from the CONAN paper are applied here, as seen throughout the code.
     * These values are then used throughout the bid making process in the agent's code.
     */
    private void generateConcessions() {
        // Gets the environment factor, a static value of 1.0. Called here as it is the same for each issue so there
        // is no need to repeatedly calculate it
        double environment = CONANUtils.getEnvironmentFactor();

        // Define an empty concessions array, large enough to fit a concession for each issue
        double[] newConcessions = new double[getIssuesSize()];

        for (int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            // If this is the last round of negotiation, then set the concession to be 0.99
            if (timeline.getTime() >= 1 - (1.0 / (1.0 * timeline.getTotalTime()))) {
                newConcessions[issueIndex] = 0.99;
            } else if (timeline.getTime() > (1.0 / (1.0 * timeline.getTotalTime()))) {
                // Otherwise, use the self and environment factors in order to calculate the desired value
                double self = CONANUtils.getSelfFactor(getIssue(issueIndex), this);

                // The self weighting, used below to normalise the concession result
                double selfWeight = CONANUtils.getSelfWeighting(
                        ((AdditiveUtilitySpace) utilitySpace).getEvaluator(getIssue(issueIndex)),
                        getIssue(issueIndex), self, timeline.getTime());

                double newConcession = (selfWeight * self) + ((1 - selfWeight) * environment);

                // If the new concession is less than the previous concession, then simply use the last one instead.
                // Otherwise, the new one may be used instead.
                if (newConcession - concessions[issueIndex] >= 0) {
                    newConcessions[issueIndex] = newConcession;
                } else {
                    newConcessions[issueIndex] = concessions[issueIndex];
                }
            }
            // If the negotiation has just begun, no concession value is calculated and the default double value is used (0.0)
        }

        // Assign the concessions object variable to be the newly generated concessions
        concessions = newConcessions;
    }

    // These two methods are used to tidy up and reduce repeated code throughout the agent
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

    /**
     * There was a problem with the default agent's utility calculation - therefore I defined a simplified seperate one
     * which works in all scenarios. It uses a simple linear function for the continuous issues and the normalised
     * double values for discrete issues.
     * @param bid - the bid to calculate a utility for
     * @return - a double value between zero and one.
     */
    @Override
    public double getUtility(Bid bid) {
        double util = 0.0;
        for(int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            Issue i = getIssue(issueIndex);

            if(i.getType() == ISSUETYPE.DISCRETE) {
                EvaluatorDiscrete eval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(i);
                eval.scaleAllValuesFrom0To1();      // Ensures the values are normalised before calculation
                // The weightings are multiplied by the (bidValue / maxDiscreteValue)
                util += weightings[issueIndex] * (eval.getDoubleValue((ValueDiscrete) bid.getValue(i)) / eval.getEvalMax());
            } else if(i.getType() == ISSUETYPE.INTEGER) {
                // The integer evaluator is used to calculate the slope (m) and offset (c) for the linear function
                EvaluatorInteger eval = (EvaluatorInteger) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(i);
                util += weightings[issueIndex] * ((eval.getSlope() * ((ValueInteger) bid.getValue(i)).getValue()) + eval.getOffset());
            }
        }

        // Ensures the value is normalised between 0 and 1.
        return Math.min(util, 1.0);
    }
}
