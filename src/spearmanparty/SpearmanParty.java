package spearmanparty;

import genius.core.utility.AbstractUtilitySpace;
import socketparty.SocketParty;
import utils.Spearman;

public class SpearmanParty extends SocketParty {
    @Override
    public String getDescription() {
        return "A party which measures the optimality of the Z3GENIUS solver";
    }

    /**
     * Use the utility space generated with the SocketParty super method in order to get estimate
     * utilities of each bid provided for building the preference model - and calculate/print the accuracy of
     * this model by measuring the correlation between the real ranking and the ranking through estimation
     * @return - the utility space estimated with the SocketParty super method
     */
    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        AbstractUtilitySpace space = super.estimateUtilitySpace();

        // Print the calculated Spearman's Coefficient
        System.out.println("Spearman Coefficient: " + Spearman.coefficient(space, userModel));

        return space;
    }
}
