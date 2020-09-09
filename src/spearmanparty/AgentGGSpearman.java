package spearmanparty;

import agents.anac.y2019.agentgg.AgentGG;
import genius.core.utility.AbstractUtilitySpace;
import utils.Spearman;

public class AgentGGSpearman extends AgentGG {
    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        AbstractUtilitySpace space = super.estimateUtilitySpace();

        System.out.println("AgentGG Spearman's: " + Spearman.coefficient(space, userModel));

        return space;
    }
}
