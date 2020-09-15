package CONAN;

import genius.core.Agent;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.analysis.pareto.IssueValue;
import genius.core.issue.*;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import z3enius.Z3niusParty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class CONAN extends Z3niusParty {
    private double[] concessions;
    HashMap<AgentID, List<Bid>> agentBids;
    HashMap<AgentID, Double> agentResponseTimes;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        agentBids = new HashMap<>();
        agentResponseTimes = new HashMap<>();
    }

    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer && !sender.equals(getPartyId())) {
            if (!agentBids.containsKey(sender)) {
                agentBids.put(sender, new ArrayList<>());
            }
            agentResponseTimes.put(sender, timeline.getTime());
            agentBids.get(sender).add(((Offer) act).getBid());
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        Bid currentBid = generateCurrentBid();
        double currentBidUtility = getUtilityWithDiscount(currentBid);

        System.out.println("--------\nGenerated Bid");
        for (int i = 0; i < getIssuesSize(); i++) {
            if (getIssue(i).getType() == ISSUETYPE.DISCRETE) {
                System.out.println(getIssue(i).getName() + ": " + ((ValueDiscrete) currentBid.getValue(getIssue(i))).getValue());
            } else if (getIssue(i).getType() == ISSUETYPE.INTEGER) {
                System.out.println(getIssue(i).getName() + ": " + ((ValueInteger) currentBid.getValue(getIssue(i))).getValue());
            }
        }
        System.out.println("Generated Bid Utility: " + currentBidUtility);

        if (possibleActions.contains(Accept.class)) {
//            for (Bid received : getLastBids()) {
                double receivedUtility = getUtilityWithDiscount(((Offer) getLastReceivedAction()).getBid());
                if (receivedUtility > currentBidUtility) {
                    System.out.println("Going to Accept Util: " + receivedUtility);
                    return new Accept(getPartyId(), ((Offer) getLastReceivedAction()).getBid());
                }
//            }
        }

        return new Offer(getPartyId(), currentBid);
    }

    @Override
    public String getDescription() {
        return "Implementation of the CONAN strategy for GENIUS";
    }

    private Bid generateCurrentBid() {
        generateConcessions();

        System.out.println("------\nConcessions: ");
        for (int i = 0; i < getIssuesSize(); i++) {
            System.out.println(getIssue(i).getName() + ": " + concessions[i]);
        }

        HashMap<Integer, Value> mappedIssueValues = new HashMap<>();

        for (int i = 0; i < getIssuesSize(); i++) {
            System.out.println(getIssue(i).getName());

            if (getIssue(i).getType() == ISSUETYPE.DISCRETE) {
                IssueDiscrete disIssue = (IssueDiscrete) getIssue(i);

                EvaluatorDiscrete disEval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) utilitySpace).getEvaluator(disIssue);

                mappedIssueValues.put(
                        disIssue.getNumber(),
                        CONAN.getDiscreteValue(disIssue.getValues(), disEval, concessions[i]));
            } else if (getIssue(i).getType() == ISSUETYPE.INTEGER) {
                IssueInteger intIssue = (IssueInteger) getIssue(i);

                mappedIssueValues.put(
                        intIssue.getNumber(),
                        CONAN.getIntegerValue(intIssue.getUpperBound(), intIssue.getLowerBound(), concessions[i]));
            }
        }

        return new Bid(getDomain(), mappedIssueValues);
    }

    private void generateConcessions() {
        double environment = CONAN.getEnvironmentFactor();
        double[] newConcessions = new double[getIssuesSize()];

        for (int issueIndex = 0; issueIndex < getIssuesSize(); issueIndex++) {
            if (timeline.getTime() >= 1 - (1.0 / (1.0 * timeline.getTotalTime()))) {
                newConcessions[issueIndex] = 0.99;
            } else if (timeline.getTime() > (1.0 / (1.0 * timeline.getTotalTime()))) {
                System.out.println("--------\nCalculating Concession");

                double self = CONAN.getSelfFactor(getIssue(issueIndex), this);
                System.out.println(getIssue(issueIndex).getName() + " Self: " + self);

                double selfWeight = CONAN.classifiedNormalisation(self);
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

    public static ValueInteger getIntegerValue(int max, int min, double concession) {
        return new ValueInteger((int) Math.round(max + ((min - max) * concession)));
    }

    public static ValueDiscrete getDiscreteValue(List<ValueDiscrete> values, EvaluatorDiscrete eval, double concession) {
        concession = 1 - concession;

        eval.normalizeAll();
        values.sort(new Comparator<ValueDiscrete>() {
            @Override
            public int compare(ValueDiscrete o1, ValueDiscrete o2) {
                return Double.compare(eval.getDoubleValue(o1), eval.getDoubleValue(o2));
            }
        });

        System.out.println("-------\nIssue Values: ");
        for (int i = 0; i < values.size(); i++) {
            System.out.println(values.get(i).getValue() + ": " + eval.getDoubleValue(values.get(i)));
        }

        if (values.size() > 1) {
            if (concession < eval.getDoubleValue(values.get(0))) {
                return values.get(0);
            }

            for (int disIndex = 1; disIndex < values.size(); disIndex++) {
                double lowValue = eval.getDoubleValue(values.get(disIndex - 1));
                double highValue = eval.getDoubleValue(values.get(disIndex));

                System.out.println("------\nComparing " + values.get(disIndex - 1).getValue() + " and " + values.get(disIndex).getValue());

                if (lowValue <= concession && concession <= highValue) {
                    if (Math.abs(highValue - concession) > Math.abs(lowValue - concession)) {
                        System.out.println("-------\nChosen " + values.get(disIndex - 1).getValue());
                        return values.get(disIndex - 1);
                    } else {
                        System.out.println("-------\nChosen " + values.get(disIndex).getValue());
                        return values.get(disIndex);
                    }
                }
            }
        } else {
            return values.get(0);
        }

        System.out.println("Nothing Chosen");
        return null;
    }

    public static double getEnvironmentFactor() {
        return 1.0;
    }

    public static double getSelfFactor(Issue issue, CONAN conan) {
        double concessionSum = 0.0;
        for (AgentID agent : conan.agentBids.keySet()) {
            concessionSum += CONAN.classifiedNormalisation(conan.getTimeLine().getCurrentTime() - conan.agentResponseTimes.get(agent));

            if (issue.getType() == ISSUETYPE.DISCRETE) {
                ValueDiscrete lastBidValue = (ValueDiscrete) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - 1).getValue(issue);
                ValueDiscrete lastLastBidValue = (ValueDiscrete) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - Math.min(conan.agentBids.get(agent).size(), 3)).getValue(issue);

                EvaluatorDiscrete disEval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) conan.getUtilitySpace()).getEvaluator(issue);
                disEval.normalizeAll();

                System.out.println("Last bid subtraction: " + (disEval.getDoubleValue(lastLastBidValue) - disEval.getDoubleValue(lastBidValue)));
                System.out.println("Min Max Subtraction: " + (1.0 * (disEval.getDoubleValue((ValueDiscrete) disEval.getMinValue()) - disEval.getDoubleValue((ValueDiscrete) disEval.getMaxValue()))));
                System.out.println("Division of the two: " + ((disEval.getDoubleValue(lastLastBidValue) - disEval.getDoubleValue(lastBidValue)) / (1.0 * (disEval.getDoubleValue((ValueDiscrete) disEval.getMinValue()) - disEval.getDoubleValue((ValueDiscrete) disEval.getMaxValue())))));
                double calculatedVal = 1.0 - ((disEval.getDoubleValue(lastLastBidValue) - disEval.getDoubleValue(lastBidValue)) / (1.0 * (disEval.getDoubleValue((ValueDiscrete) disEval.getMinValue()) - disEval.getDoubleValue((ValueDiscrete) disEval.getMaxValue()))));

                System.out.println("1 - previous: " + calculatedVal);

                concessionSum += CONAN.classifiedNormalisation(calculatedVal);
            } else if (issue.getType() == ISSUETYPE.INTEGER) {
                ValueInteger lastBidValue = (ValueInteger) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - 1).getValue(issue);
                ValueInteger lastLastBidValue = (ValueInteger) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - 2).getValue(issue);

                IssueInteger intIssue = (IssueInteger) issue;
                double calculatedVal = 1.0 - (Math.abs(lastLastBidValue.getValue() - lastBidValue.getValue()) / (1.0 * (intIssue.getUpperBound() - intIssue.getLowerBound())));
                System.out.println(calculatedVal);
                concessionSum += CONAN.classifiedNormalisation(calculatedVal);
            }
        }

        System.out.println(concessionSum);
        double normalisedConcession = (concessionSum - (2.0 * conan.agentBids.keySet().size())) / (4.0 * conan.agentBids.keySet().size());
        System.out.println(normalisedConcession);

        return (1.0 + conan.getTimeLine().getTime() + normalisedConcession + 1.0) / 4.0;
    }

    public static double classifiedNormalisation(double value) {
        if (value > 0.0 && value <= (1 / 3.0)) {
            return 1;
        } else if (value > (1 / 3.0) && value <= (2 / 3.0)) {
            return 2;
        } else if (value > (2 / 3.0) && value <= 1.0) {
            return 3;
        }
        return 0;
    }
}
