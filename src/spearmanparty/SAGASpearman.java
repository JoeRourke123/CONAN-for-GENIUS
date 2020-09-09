package spearmanparty;

import agents.anac.y2019.saga.SAGA;
import genius.core.utility.AbstractUtilitySpace;
import utils.Spearman;

public class SAGASpearman extends SAGA {
    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        AbstractUtilitySpace space = super.estimateUtilitySpace();

        System.out.println("SAGA Spearman's: " + Spearman.coefficient(space, userModel));

        return space;
    }
}
