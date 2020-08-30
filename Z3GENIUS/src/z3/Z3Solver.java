package z3;

import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.NumeralFormula;

import java.util.ArrayList;
import java.util.List;

public class Z3Solver {
    private List<BooleanFormula> constraints;

    private List<NumeralFormula.RationalFormula> weightings;

    private List<List<NumeralFormula.RationalFormula>> issues;

    private List<List<NumeralFormula.RationalFormula>> bidValues;

    private String[] issueTypes;

    private LogManager logger;
    private ShutdownManager shutdown;

    public Z3Solver(
        List<NumeralFormula.RationalFormula> w,
        List<List<NumeralFormula.RationalFormula>> i,
        List<List<NumeralFormula.RationalFormula>> b,
        String[] t
    ) {
        try {
            Configuration config = Configuration.fromCmdLineArguments(new String[0]);
            logger = BasicLogManager.create(config);
            shutdown = ShutdownManager.create();
        } catch(InvalidConfigurationException e) {
            System.out.println("Something went wrong with the configuration of Z3");
            System.err.println(e);
        }

        this.weightings = w;
        this.issues = i;
        this.bidValues = b;
        this.issueTypes = t;
        this.constraints = new ArrayList<>();
    }

    public void estimate() {

    }

    public NumeralFormula.RationalFormula[] getWeightings() {
        return weightings;
    }

    public NumeralFormula.RationalFormula[][] getIssues() {
        return issues;
    }
}
