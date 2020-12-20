package z3;

import genius.core.Bid;
import genius.core.issue.*;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Z3Solver {
    private List<BooleanFormula> constraints;

    private NumeralFormula[] weightings;
    private NumeralFormula.RationalFormula[] bidUtilities;

    private NumeralFormula.RationalFormula[][] discreteIssueValues;

    private NumeralFormula.RationalFormula[][] intIssueBounds;
    private NumeralFormula.RationalFormula[][] intIssueUtilities;
    private NumeralFormula.RationalFormula[] intIssueSlopes;

    private LogManager logger;
    private ShutdownManager shutdown;

    private RationalFormulaManager nums;
    private BooleanFormulaManager bools;
    private SolverContext context;
    private ProverEnvironment prover;

    private NumeralFormula ONE;
    private NumeralFormula ZERO;

    /**
     * Initialises all the required class variables for the Z3 solver to generate an appropriate model
     *
     * @param bids   - an array list of bids - only used to get the required sizes for the internal arrays
     * @param issues - an array list of issues - " "
     */
    public Z3Solver(
            List<Bid> bids,
            List<Issue> issues
    ) {
        try {
            // These are objects required for the use of the Java-SMT library
            Configuration config = Configuration.fromCmdLineArguments(new String[0]);
            logger = BasicLogManager.create(config);
            shutdown = ShutdownManager.create();

            // These allow constraints to be applied to the model
            context = SolverContextFactory.createSolverContext(config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.Z3);
            nums = context.getFormulaManager().getRationalFormulaManager(); // For adding numeric constraints
            bools = context.getFormulaManager().getBooleanFormulaManager(); // For adding boolean/logical constraints

            ONE = nums.makeNumber(1.0);
            ZERO = nums.makeNumber(0.0);
        } catch (InvalidConfigurationException e) {
            System.out.println("Something went wrong with the configuration of Z3");
            System.err.println(e);
        }

        // Stores the rational formula objects (an encapsulation of numeric values for JavaSMT) used for generating
        // the various different values required for GENIUS
        this.weightings = new NumeralFormula.RationalFormula[issues.size()];

        this.bidUtilities = new NumeralFormula.RationalFormula[bids.size()];

        this.discreteIssueValues = new NumeralFormula.RationalFormula[issues.size()][];
        this.intIssueSlopes = new NumeralFormula.RationalFormula[issues.size()];
        this.intIssueUtilities = new NumeralFormula.RationalFormula[issues.size()][2];

        // The constraints use an ArrayList over a primitive array due to it's variable length
        this.constraints = new ArrayList<>();
    }

    /**
     * Given a list of bid objects and issue objects, a model is generated which can estimate the preferences
     * of a user in the given domain
     *
     * @param bids   - the pre-populated list of bids from a message sent from the client to the program
     * @param issues - the generated list of issues, either discrete or continuous
     * @return - a list of values assigned by the model
     */
    public List<Model.ValueAssignment> estimate(List<Bid> bids, List<Issue> issues, double lowBidUitility, double highBidUtility) {
        // Makes a call to a method for applying all the required constraints for the weighting values
        // for each issue
        addWeightConstraints();

        // For each issue, add the appropriate constraints (depending on whether it is discrete or continuous)
        int index = 0;
        for (Issue issue : issues) {
            if (issue.getType() == ISSUETYPE.INTEGER) {
                addIssueValues((IssueInteger) issue, index);
            } else if (issue.getType() == ISSUETYPE.DISCRETE) {
                addIssueValues((IssueDiscrete) issue, index);
            }

            index++;
        }

        // For each bid, add the constraints required for
        for (int i = 0; i < bids.size(); i++) {
            Bid bid = bids.get(i);
            bidUtilityConstraint(i, bid);
        }

        bidOrderConstraint(bidUtilities, lowBidUitility, highBidUtility);

        prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);

        try {
            for (BooleanFormula b : constraints) {
                prover.addConstraint(b);
            }

            boolean isUnsat = prover.isUnsat();
            assert !isUnsat;
            return prover.getModelAssignments();
        } catch (InterruptedException | SolverException e) {
            System.err.println("Model is not satisfiable");
            System.err.println(e);
        }

        return null;
    }

    private void bidUtilityConstraint(int bidIndex, Bid bid) {
        List<NumeralFormula> utilSum = new ArrayList<>();

        for (int i = 0; i < bid.getIssues().size(); i++) {
            Issue issue = bid.getIssues().get(i);

            if (issue.getType() == ISSUETYPE.DISCRETE) {
                int valueIndex = ((IssueDiscrete) issue).getValueIndex((ValueDiscrete) bid.getValue(issue));
                utilSum.add(nums.multiply(
                    discreteIssueValues[i][valueIndex],
                    weightings[i]
                ));
            } else if (issue.getType() == ISSUETYPE.INTEGER) {
                NumeralFormula.RationalFormula bidValue = nums.makeNumber((((ValueInteger) bid.getValue(issue)).getValue()));
                utilSum.add(nums.multiply(
                    nums.add(nums.multiply(intIssueSlopes[i], bidValue), intIssueUtilities[i][0]),
                    weightings[i]
                ));
            }
        }

        bidUtilities[bidIndex] = nums.makeVariable("bid-" + bidIndex);
        constraints.add(nums.equal(nums.sum(utilSum), bidUtilities[bidIndex]));
    }

    private void bidOrderConstraint(NumeralFormula.RationalFormula[] bids, double lowUtil, double highUtil) {
        constraints.add(
                nums.equal(bids[0], nums.makeNumber(lowUtil))
        );

        for (int i = 0; i < bids.length - 1; i++) {
            constraints.add(
                    nums.lessOrEquals(bids[i], bids[i + 1])
            );
        }

        constraints.add(
                nums.equal(bids[bids.length - 1], nums.makeNumber(highUtil))
        );
    }

    public void addWeightConstraints() {
        NumeralFormula.RationalFormula weightSum = nums.makeNumber(0.0);
        for (int i = 0; i < weightings.length; i++) {
            weightings[i] = nums.makeVariable("weight-" + i);

            isBetweenOneZero(weightings[i], false, weightings.length == 1);

            weightSum = nums.add(weightSum, weightings[i]);
        }

        constraints.add(nums.equal(weightSum, nums.makeNumber(1.0)));
    }

    private void isBetweenOneZero(NumeralFormula val, boolean includeZero, boolean includeOne) {
        constraints.add(
                (includeOne) ? nums.lessOrEquals(val, ONE) : nums.lessThan(val, ONE)
        );

        constraints.add(
                (includeZero) ? nums.greaterOrEquals(val, ZERO) : nums.greaterThan(val, ZERO)
        );
    }

    private void addIssueValues(IssueDiscrete issue, int issueIndex) {
        discreteIssueValues[issueIndex] = new NumeralFormula.RationalFormula[issue.getNumberOfValues()];

        for (int i = 0; i < issue.getNumberOfValues(); i++) {
            discreteIssueValues[issueIndex][i] = nums.makeVariable("issue-" + issueIndex + "-" + i);
            constraints.add(nums.greaterThan(
                    discreteIssueValues[issueIndex][i], ZERO
            ));
//            isBetweenOneZero(discreteIssueValues[issueIndex][i], false, true);
        }
    }

    private void addIssueValues(IssueInteger issue, int issueIndex) {
        NumeralFormula lower = nums.makeNumber(Double.valueOf(issue.getLowerBound()));
        NumeralFormula upper = nums.makeNumber(Double.valueOf(issue.getUpperBound()));

        intIssueUtilities[issueIndex][0] = nums.makeVariable("issue-" + issueIndex + "-minutil");
        isBetweenOneZero(intIssueUtilities[issueIndex][0], true, false);
        intIssueUtilities[issueIndex][1] = nums.makeVariable("issue-" + issueIndex + "-maxutil");
        isBetweenOneZero(intIssueUtilities[issueIndex][1], false, true);

        constraints.add(
                nums.lessThan(intIssueUtilities[issueIndex][0], intIssueUtilities[issueIndex][1])
        );

        intIssueSlopes[issueIndex] = nums.makeVariable("issue-" + issueIndex + "-slope");
        constraints.add(nums.equal(
                intIssueSlopes[issueIndex],
                nums.divide(
                        nums.subtract(intIssueUtilities[issueIndex][1], intIssueUtilities[issueIndex][0]),
                        nums.subtract(intIssueBounds[issueIndex][1], intIssueBounds[issueIndex][0])
                )
        ));
    }

    public void close() {
        try {
            context.close();
            prover.close();
            shutdown.requestShutdown("End of Use");
        } catch (Error | Exception e) {
            System.err.println("An error occured while closing Z3");
            System.err.println(e);
        }
    }
}

