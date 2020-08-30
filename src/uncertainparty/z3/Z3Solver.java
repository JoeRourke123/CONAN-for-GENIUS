//package uncertainparty.z3;
//
//import genius.core.Bid;
//import genius.core.Domain;
//import genius.core.issue.*;
//import genius.core.uncertainty.BidRanking;
//import genius.core.utility.AdditiveUtilitySpace;
//import genius.core.utility.EvaluatorDiscrete;
//import genius.core.utility.EvaluatorInteger;
//import org.sosy_lab.common.ShutdownManager;
//import org.sosy_lab.common.configuration.Configuration;
//import org.sosy_lab.common.configuration.InvalidConfigurationException;
//import org.sosy_lab.common.log.BasicLogManager;
//import org.sosy_lab.common.log.LogManager;
//import org.sosy_lab.java_smt.SolverContextFactory;
//import org.sosy_lab.java_smt.api.*;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class Z3Solver {
//    private List<BooleanFormula> constraints = new ArrayList<>();
//
//    private NumeralFormula.RationalFormula[] bidUtilities;
//    private NumeralFormula.RationalFormula[] weightings;
//
//    private NumeralFormula.RationalFormula[][] discreteIssueValues;
//
//    private NumeralFormula.RationalFormula[][] intIssueBounds;
//    private NumeralFormula.RationalFormula[][] intIssueUtilities;
//    private NumeralFormula.RationalFormula[] intIssueSlopes;
//
//    private Configuration config;
//    private LogManager logger;
//    private ShutdownManager shutdown;
//
//    public Z3Solver() {
//        try {
//            config = Configuration.fromCmdLineArguments(new String[0]);
//            logger = BasicLogManager.create(config);
//            shutdown = ShutdownManager.create();
//        } catch(InvalidConfigurationException e) {
//            System.out.println("Something went wrong with the configuration of Z3");
//            System.err.println(e);
//        } finally {
//
//        }
//    }
//
//    public AdditiveUtilitySpace estimatePreferences(BidRanking bidRanking, Domain domain) {
//        try (SolverContext context = SolverContextFactory.createSolverContext(
//                config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.Z3)) {
//            RationalFormulaManager nmgr = context.getFormulaManager().getRationalFormulaManager();
//            BooleanFormulaManager bmgr = context.getFormulaManager().getBooleanFormulaManager();
//
//            initialiseArrays(domain.getIssues().size(), bidRanking.getSize());
//
//            int index = 0;
//            NumeralFormula.RationalFormula weightSum = nmgr.makeNumber(0.0);
//
//            for(Issue issue : domain.getIssues()) {
//                addWeighting(nmgr, bmgr, issue, index);
//                weightSum = nmgr.add(weightSum, weightings[index]);
//
//                if(issue.getType() == ISSUETYPE.INTEGER) {
//                    addIssueValues(nmgr, bmgr, (IssueInteger) issue, index);
//                } else if(issue.getType() == ISSUETYPE.DISCRETE) {
//                    addIssueValues(nmgr, bmgr, (IssueDiscrete) issue, index);
//                }
//
//                index++;
//            }
//
//            constraints.add(nmgr.equal(
//                nmgr.makeNumber(1.0), weightSum
//            ));
//
//            for(int i = 0; i < bidRanking.getBidOrder().size(); i++) {
//                bidUtilities[i] = nmgr.makeVariable("bid-" + i);
//                Bid bid = bidRanking.getBidOrder().get(i);
//                bidUtilityConstraint(bidUtilities[i], bid, nmgr);
//            }
//
//            bidOrderConstraint(bidUtilities, nmgr);
//
//            try (ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
//                for(BooleanFormula b : constraints) {
//                    prover.addConstraint(b);
//                }
//
//                boolean isUnsat = prover.isUnsat();
//                assert !isUnsat;
//                try (Model model = prover.getModel()) {
//                    System.out.println("WEIGHTINGS");
//                    for(int i = 0; i < domain.getIssues().size(); i++) {
//                        System.out.println(domain.getIssues().get(i).getName() + " - " + model.evaluate(weightings[i]));
//                    }
//                    System.out.println();
//
//                    return getUtilitySpace(model, domain);
//                }
//            } catch(InterruptedException | SolverException e) {
//                System.err.println("Model is not satisfiable");
//                System.err.println(e);
//            }
//        } catch(InvalidConfigurationException e) {
//            System.out.println("Something went wrong with the configuration of Z3");
//            System.err.println(e);
//        }
//
//        return null;
//    }
//
//    private AdditiveUtilitySpace getUtilitySpace(Model model, Domain domain) {
//        AdditiveUtilitySpace space = new AdditiveUtilitySpace(domain);
//        List<Issue> issues = domain.getIssues();
//
//        for(int i = 0; i < issues.size(); i++) {
//            Issue issue = issues.get(i);
//
//            if(issue.getType() == ISSUETYPE.DISCRETE) {
//                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
//                for(int j = 0; j < discreteIssue.getNumberOfValues(); j++) {
//                    ValueDiscrete discreteValue = discreteIssue.getValue(j);
//
//                    EvaluatorDiscrete discreteEval = new EvaluatorDiscrete();
//                    discreteEval.setEvaluationDouble(discreteValue, model.evaluate(discreteIssueValues[i][j]).doubleValue());
//                    space.addEvaluator(issue, discreteEval);
//                }
//            } else if(issue.getType() == ISSUETYPE.INTEGER) {
//                EvaluatorInteger integerEval = new EvaluatorInteger();
//                integerEval.setLinearFunction(model.evaluate(intIssueUtilities[i][0]).doubleValue(),
//                                              model.evaluate(intIssueUtilities[i][1]).doubleValue());
//                space.addEvaluator(issue, integerEval);
//            }
//        }
//
//        return space;
//    }
//
//    private void addWeighting(RationalFormulaManager nmgr, BooleanFormulaManager bmgr, Issue issue, int index) {
//        weightings[index] = nmgr.makeVariable("weight-" + index);
//        isBetweenOneZero(weightings[index], nmgr, bmgr, false);
//    }
//
//    private void addIssueValues(RationalFormulaManager nmgr, BooleanFormulaManager bmgr, IssueDiscrete issue, int issueIndex) {
//        discreteIssueValues[issueIndex] = new NumeralFormula.RationalFormula[issue.getNumberOfValues()];
//
//        for(int i = 0; i < issue.getNumberOfValues(); i++) {
//            discreteIssueValues[issueIndex][i] = nmgr.makeVariable("issue-" + issueIndex + "-" + i);
//            constraints.add(nmgr.greaterThan(
//                discreteIssueValues[issueIndex][i], nmgr.makeNumber(0.0)
//            ));
//        }
//    }
//
//    private void addIssueValues(RationalFormulaManager nmgr, BooleanFormulaManager bmgr, IssueInteger issue, int issueIndex) {
//        intIssueBounds[issueIndex][0] = nmgr.makeNumber(Double.valueOf(issue.getLowerBound()));
//        intIssueBounds[issueIndex][1] = nmgr.makeNumber(Double.valueOf(issue.getUpperBound()));
//
//        intIssueUtilities[issueIndex][0] = nmgr.makeVariable("issue-" + issueIndex + "-minutil");
//        isBetweenOneZero(intIssueUtilities[issueIndex][0], nmgr, bmgr, true);
//        intIssueUtilities[issueIndex][1] = nmgr.makeVariable("issue-" + issueIndex + "-maxutil");
//        isBetweenOneZero(intIssueUtilities[issueIndex][1], nmgr, bmgr, true);
//
//        intIssueSlopes[issueIndex] = nmgr.makeVariable("issue-" + issueIndex + "-slope");
//        constraints.add(nmgr.equal(
//            intIssueSlopes[issueIndex],
//            nmgr.divide(
//                nmgr.subtract(intIssueUtilities[issueIndex][1], intIssueUtilities[issueIndex][0]),
//                nmgr.subtract(intIssueBounds[issueIndex][1], intIssueBounds[issueIndex][0])
//            )
//        ));
//    }
//
//    private void bidUtilityConstraint(NumeralFormula.RationalFormula bidFormula, Bid bid, RationalFormulaManager nmgr) {
//        NumeralFormula.RationalFormula util = nmgr.makeNumber(0.0);
//        for(int i = 0; i < bid.getIssues().size(); i++) {
//            Issue issue = bid.getIssues().get(i);
//
//            if(issue.getType() == ISSUETYPE.DISCRETE) {
//                int valueIndex = ((IssueDiscrete) issue).getValueIndex((ValueDiscrete) bid.getValue(issue));
//                NumeralFormula.RationalFormula bidValue = nmgr.divide(discreteIssueValues[i][valueIndex], discreteIssueValues[i][discreteIssueValues[i].length - 1]);
//                util = nmgr.add(util, bidValue);
//            } else if(issue.getType() == ISSUETYPE.INTEGER) {
//                NumeralFormula.RationalFormula bidValue = nmgr.makeNumber((((ValueInteger) bid.getValue(issue)).getValue()));
//                util = nmgr.add(util,
//                                nmgr.add(nmgr.multiply(intIssueSlopes[i], bidValue), intIssueUtilities[i][0]));
//            }
//        }
//
//        constraints.add(
//            nmgr.equal(
//                bidFormula,
//                util
//            )
//        );
//    }
//
//    private void bidOrderConstraint(NumeralFormula.RationalFormula[] bids, RationalFormulaManager nmgr) {
//        for(int i = 0; i < bids.length - 1; i--) {
//            constraints.add(
//                nmgr.lessOrEquals(bids[i], bids[i + 1])
//            );
//        }
//    }
//
//    private void initialiseArrays(int utilCount, int bidCount) {
//        bidUtilities = new NumeralFormula.RationalFormula[bidCount];
//        weightings = new NumeralFormula.RationalFormula[utilCount];
//
//        discreteIssueValues = new NumeralFormula.RationalFormula[utilCount][];
//        intIssueBounds = new NumeralFormula.RationalFormula[utilCount][2];
//        intIssueUtilities = new NumeralFormula.RationalFormula[utilCount][2];
//        intIssueSlopes = new NumeralFormula.RationalFormula[utilCount];
//    }
//
//    private void isBetweenOneZero(NumeralFormula.RationalFormula val, RationalFormulaManager nmgr, BooleanFormulaManager bmgr, boolean includeZero) {
//        final NumeralFormula.RationalFormula ONE = nmgr.makeNumber(1.0);
//        final NumeralFormula.RationalFormula ZERO = nmgr.makeNumber(0.0);
//
//        constraints.add(
//            nmgr.lessOrEquals(val, ONE)
//        );
//        constraints.add(
//            (includeZero) ? nmgr.greaterOrEquals(val, ZERO) : nmgr.greaterThan(val, ZERO)
//        );
//    }
//}
