package CONAN;

import genius.core.AgentID;
import genius.core.issue.*;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import genius.core.utility.EvaluatorDiscrete;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CONANUtils {
    /**
     * Use the CONAN paper's heuristics in order to generate a new ValueInteger object based on the calculated concession value
     * and available data on the domain
     * @param max - the initial amount
     * @param min - the reservation amount
     * @param concession - the calculated concession value for the required issue
     * @return - a ValueInteger object that can be placed into a bid object and sent as an offer
     */
    public static ValueInteger getIntegerValue(int max, int min, double concession) {
        return new ValueInteger((int) Math.round(max + ((min - max) * concession)));
    }

    /**
     * A slightly adapted version of the CONAN heuristics, for implementing discrete value generation. Uses normalised
     * values for the values under a discrete issue - with a GENIUS Evaluator - and then the same CONAN formulae can be
     * used.
     * @param issue - the current issue the bid value is being generated for
     * @param eval - the evaluator which is fetched from the user's utility space
     * @param concession - the concession value calculated for this specific issue
     * @return - a ValueDiscrete object which can be used in a Bid object
     */
    public static ValueDiscrete getDiscreteValue(IssueDiscrete issue, EvaluatorDiscrete eval, double concession) {
        // We want to achieve a value which is as close to the concession as possible, but also the highest discrete
        // issue possible - therefore the concession needs to be inverted.
        concession = 1 - concession;

        // There was a problem here with sorting the issue value array, therefore a new one was required to prevent
        // breaking the domain and causing issues with the GENIUS simulation
        List<ValueDiscrete> values = new ArrayList<>();
        values.addAll(issue.getValues());

        // Ensures all the evaluator values are between 0 and 1.
        eval.normalizeAll();

        // This sorts the values list by the evaluated utility of each possible issue value.
        values.sort(new Comparator<ValueDiscrete>() {
            @Override
            public int compare(ValueDiscrete o1, ValueDiscrete o2) {
                return Double.compare(eval.getDoubleValue(o1), eval.getDoubleValue(o2));
            }
        });

        if (values.size() > 1) {
            // Sometimes the concession is less than the smallest value, therefore this value is returned
            if (concession < eval.getDoubleValue(values.get(0))) {
                return values.get(0);
            }

            // Otherwise, search through each value to find the closest alignment with the concession value compared
            // to the double evaluation of said values.
            for (int disIndex = 1; disIndex < values.size(); disIndex++) {
                double lowValue = eval.getDoubleValue(values.get(disIndex - 1));
                double highValue = eval.getDoubleValue(values.get(disIndex));

                // For each iteration, the previuous and current values are compared in relation to the concession
                if (lowValue <= concession && concession <= highValue) {
                    if (Math.abs(highValue - concession) > Math.abs(lowValue - concession)) {
                        // The one which is closest is then chosen
                        return issue.getValue(issue.getValueIndex(values.get(disIndex - 1)));
                    } else {
                        return issue.getValue(issue.getValueIndex(values.get(disIndex)));
                    }
                }
            }
        }

        // If there is only one value in the array, or the search fails to find an appropriate value, return the highest
        // possible discrete value
        return (ValueDiscrete) eval.getMaxValue();
    }

    /**
     * The CONAN environment factor uses information about the number of "competitor" agents and the number of other
     * possible "seller" agents in order to calculate a normalised value. This value is meant to help the agent decide
     * whether to pursue alternative negotiations, however this is not possible with GENIUS so the environment factor
     * is a static value.
     * @return - 1.0: the static environment factor value I am using
     */
    public static double getEnvironmentFactor() {
        return 1.0;
    }

    /**
     * The CONAN self factor provides the agent with an idea of it's current position in the negotiation, and it's own
     * success/failure. The paper uses the number of reserved offers made in order to measure success, however this is
     * simply omitted from this version as the function also uses the concession rate of the opponents, eagerness to
     * negotiate and the current time. The returned value should be between 0 and 1.
     * @param issue - the issue the self factor is being generated for, as they are calculated independently
     * @param conan - the CONAN party object, as lots of it's attributes are required within the function
     * @return - a normalised self factor value => [0,1]
     */
    public static double getSelfFactor(Issue issue, ConanParty conan) {
        double concessionSum = 0.0;
        for (AgentID agent : conan.agentBids.keySet()) {
            // Add the classified response time of the current opponent to the concession rate sum (which is further normalised later)
            concessionSum += CONANUtils.classifiedNormalisation(conan.getTimeLine().getCurrentTime() - conan.agentResponseTimes.get(agent));

            if (issue.getType() == ISSUETYPE.DISCRETE) {
                // Fetches the last two generated bid values by the current iteration's opponent
                ValueDiscrete lastBidValue = (ValueDiscrete) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - 1).getValue(issue);
                ValueDiscrete lastLastBidValue = (ValueDiscrete) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - Math.min(conan.agentBids.get(agent).size(), 3)).getValue(issue);

                EvaluatorDiscrete disEval = (EvaluatorDiscrete) ((AdditiveUtilitySpace) conan.getUtilitySpace()).getEvaluator(issue);
                disEval.normalizeAll();

                // Calculate how much the opponent has conceded over the last few bids using the CONAN paper formulae
                double calculatedVal = 1.0 - ((disEval.getDoubleValue(lastLastBidValue) - disEval.getDoubleValue(lastBidValue)) / (1.0 * (disEval.getDoubleValue((ValueDiscrete) disEval.getMinValue()) - disEval.getDoubleValue((ValueDiscrete) disEval.getMaxValue()))));

                // Normalise this value with the classification function
                concessionSum += CONANUtils.classifiedNormalisation(calculatedVal);
            } else if (issue.getType() == ISSUETYPE.INTEGER) {
                // The last two values for this received from this opponent
                ValueInteger lastBidValue = (ValueInteger) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - 1).getValue(issue);
                ValueInteger lastLastBidValue = (ValueInteger) conan.agentBids.get(agent).get(conan.agentBids.get(agent).size() - Math.min(conan.agentBids.get(agent).size(), 3)).getValue(issue);

                IssueInteger intIssue = (IssueInteger) issue;
                // Like the discrete version, the qualitative values are retrieved, normalised, and added to the concession sum
                double calculatedVal = 1.0 - (Math.abs(lastLastBidValue.getValue() - lastBidValue.getValue()) / (1.0 * (intIssue.getUpperBound() - intIssue.getLowerBound())));
                concessionSum += CONANUtils.classifiedNormalisation(calculatedVal);
            }
        }

        // The sum value is normalised using the formula in the CONAN paper to make it between 0 and 1
        double normalisedConcession = (concessionSum - (2.0 * conan.agentBids.keySet().size())) / (4.0 * conan.agentBids.keySet().size());

        // The self factor is generated using the current time, opponent's concession rate, and the eagerness to reach
        // an agreement (in GENIUS, this is the discount value).
        return (1.0 + conan.getTimeLine().getTime() + normalisedConcession + conan.getUtilitySpace().getDiscountFactor()) / 4.0;
    }

    /**
     * Many times in the CONAN paper, values between 0 and 1 are classified into integers from 1 - 3 depending on the
     * bounds of the real number. To prevent repeated code, this has been placed into a simple util function
     * @param value - the real number between (0, 1]
     * @return - a normalised integer value in the following set {1, 2, 3}
     */
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

    /**
     * The self factor and environment factors are used to calculate the concession rate in standard situations, this
     * value must be normalised - therefore a self weighting is used to make this sum between 0 and 1. This weighting
     * is based on the minimum and maximum values in relation to the current time and is calculated independently
     * for each issue
     * @param eval - an evaluator object used only for the discrete issue to calculate the minimum and maximum values
     * @param issue - the current issue being calculated for
     * @param self - the self factor, which is classified and used as part of the weighting calcualation
     * @param time - the current time retrieved through GENIUS, between 0 and 1 depending on the elapsed/remaining time
     * @return - a real value between 0 and 1 which is used in calculations of concession
     */
    public static double getSelfWeighting(Evaluator eval, Issue issue, double self, double time) {
        double weighting;
        if(issue.getType() == ISSUETYPE.INTEGER) {
            IssueInteger intIssue = (IssueInteger) issue;
            weighting = (intIssue.getUpperBound() - intIssue.getLowerBound()) / time;
        } else if(issue.getType() == ISSUETYPE.DISCRETE) {
            EvaluatorDiscrete disEval = (EvaluatorDiscrete) eval;
            disEval.normalizeAll();
            // (upper - lower) / currentTime
            weighting = (disEval.getDoubleValue((ValueDiscrete) disEval.getMaxValue()) -
                    disEval.getDoubleValue((ValueDiscrete) disEval.getMinValue())) / time;
        } else {
            // In the very bizarre scenario where the issue is of a different type
            weighting = 0.5;
        }

        // Uses a string of ternary statements in order to decide the amount to weight the returned value, based on the
        // value of the self factor
        double normalisedSelf = CONANUtils.classifiedNormalisation(self);
        return (normalisedSelf == 1) ? weighting * 0.75 : ((normalisedSelf == 2) ? weighting * 0.5 : weighting * 0.25);
    }
}
