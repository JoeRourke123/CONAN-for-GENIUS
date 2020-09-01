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
import java.util.List;

public class Z3Solver {
    private List<BooleanFormula> constraints;

    private NumeralFormula.RationalFormula[] weightings;
    private NumeralFormula.RationalFormula[] bidUtilities;

    private NumeralFormula.RationalFormula[][] discreteIssueValues;

    private NumeralFormula.RationalFormula[][] intIssueBounds;
    private NumeralFormula.RationalFormula[][] intIssueUtilities;
    private NumeralFormula.RationalFormula[] intIssueSlopes;

    private LogManager logger;
    private ShutdownManager shutdown;

    public RationalFormulaManager nums;
    public BooleanFormulaManager bools;
    private SolverContext context;

    public Z3Solver(
            List<Bid> bids,
            List<Issue> issues
    ) {
        try {
            Configuration config = Configuration.fromCmdLineArguments(new String[0]);
            logger = BasicLogManager.create(config);
            shutdown = ShutdownManager.create();

            context = SolverContextFactory.createSolverContext(config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.Z3);
            nums = context.getFormulaManager().getRationalFormulaManager();
            bools = context.getFormulaManager().getBooleanFormulaManager();
        } catch (InvalidConfigurationException e) {
            System.out.println("Something went wrong with the configuration of Z3");
            System.err.println(e);
        }

        this.weightings = new NumeralFormula.RationalFormula[issues.size()];

        this.bidUtilities = new NumeralFormula.RationalFormula[bids.size()];

        this.discreteIssueValues = new NumeralFormula.RationalFormula[issues.size()][];
        this.intIssueBounds = new NumeralFormula.RationalFormula[issues.size()][];
        this.intIssueSlopes = new NumeralFormula.RationalFormula[issues.size()];
        this.intIssueUtilities = new NumeralFormula.RationalFormula[issues.size()][];

        this.constraints = new ArrayList<>();
    }

    public Model estimate(List<Bid> bids, List<Issue> issues) {
        addWeightConstraints();

        int index = 0;
        for (Issue issue : issues) {
            if (issue.getType() == ISSUETYPE.INTEGER) {
                addIssueValues((IssueInteger) issue, index);
            } else if (issue.getType() == ISSUETYPE.DISCRETE) {
                addIssueValues((IssueDiscrete) issue, index);
            }

            index++;
        }

        for (int i = 0; i < bids.size(); i++) {
            bidUtilities[i] = nums.makeVariable("bid-" + i);
            Bid bid = bids.get(i);
            bidUtilityConstraint(bidUtilities[i], bid);
        }

        bidOrderConstraint(bidUtilities);

        try (ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            for (BooleanFormula b : constraints) {
                prover.addConstraint(b);
            }

            boolean isUnsat = prover.isUnsat();
            assert !isUnsat;
            try (Model model = prover.getModel()) {
                return model;
            }
        } catch (InterruptedException | SolverException e) {
            System.err.println("Model is not satisfiable");
            System.err.println(e);
        }

        return null;
    }

    private void bidUtilityConstraint(NumeralFormula.RationalFormula bidFormula, Bid bid) {
        NumeralFormula.RationalFormula util = nums.makeNumber(0.0);
        for(int i = 0; i < bid.getIssues().size(); i++) {
            Issue issue = bid.getIssues().get(i);

            if(issue.getType() == ISSUETYPE.DISCRETE) {
                int valueIndex = ((IssueDiscrete) issue).getValueIndex((ValueDiscrete) bid.getValue(issue));
                NumeralFormula.RationalFormula bidValue = nums.divide(discreteIssueValues[i][valueIndex], discreteIssueValues[i][discreteIssueValues[i].length - 1]);
                util = nums.add(util, bidValue);
            } else if(issue.getType() == ISSUETYPE.INTEGER) {
                NumeralFormula.RationalFormula bidValue = nums.makeNumber((((ValueInteger) bid.getValue(issue)).getValue()));
                util = nums.add(util,
                                nums.add(nums.multiply(intIssueSlopes[i], bidValue), intIssueUtilities[i][0]));
            }
        }

        constraints.add(
            nums.equal(
                bidFormula,
                util
            )
        );
    }

    private void bidOrderConstraint(NumeralFormula.RationalFormula[] bids) {
        for(int i = 0; i < bids.length - 1; i--) {
            constraints.add(
                nums.lessOrEquals(bids[i], bids[i + 1])
            );
        }
    }

    public void addWeightConstraints() {
        NumeralFormula.RationalFormula weightSum = nums.makeNumber(0.0);
        for (int i = 0; i < weightings.length; i++) {
            weightings[i] = nums.makeVariable("weight-" + i);

            isBetweenOneZero(weightings[i], false, false);

            weightSum = nums.add(weightSum, weightings[i]);
        }

        constraints.add(nums.equal(weightSum, nums.makeNumber(1.0)));
    }

    private void isBetweenOneZero(NumeralFormula.RationalFormula val, boolean includeZero, boolean includeOne) {
        final NumeralFormula.RationalFormula ONE = nums.makeNumber(1.0);
        final NumeralFormula.RationalFormula ZERO = nums.makeNumber(0.0);

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
                    discreteIssueValues[issueIndex][i], nums.makeNumber(0.0)
            ));
        }
    }

    private void addIssueValues(IssueInteger issue, int issueIndex) {
        intIssueBounds[issueIndex][0] = nums.makeNumber(Double.valueOf(issue.getLowerBound()));
        intIssueBounds[issueIndex][1] = nums.makeNumber(Double.valueOf(issue.getUpperBound()));

        intIssueUtilities[issueIndex][0] = nums.makeVariable("issue-" + issueIndex + "-minutil");
        isBetweenOneZero(intIssueUtilities[issueIndex][0], true, false);
        intIssueUtilities[issueIndex][1] = nums.makeVariable("issue-" + issueIndex + "-maxutil");
        isBetweenOneZero(intIssueUtilities[issueIndex][1], false, false);

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
        context.close();
    }
}
