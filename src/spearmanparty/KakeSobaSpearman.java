package spearmanparty;

import agents.anac.y2019.kakesoba.KakeSoba;
import agents.anac.y2019.saga.SAGA;
import genius.core.utility.AbstractUtilitySpace;
import utils.Spearman;

public class KakeSobaSpearman extends KakeSoba {
    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        AbstractUtilitySpace space = super.estimateUtilitySpace();

        System.out.println("KakeSoba Spearman's: " + Spearman.coefficient(space, userModel));

        return space;
    }
}
