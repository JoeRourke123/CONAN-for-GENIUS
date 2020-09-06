package averageparty;

import genius.core.Bid;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.utility.AbstractUtilitySpace;
import socketparty.SocketParty;

import java.util.List;

public class AverageParty extends SocketParty {
    @Override
    public String getDescription() {
        return "Calculates the differences between the estimated and real utilities";
    }

    /**
     * Use the SocketParty super method in order to generate the appropriate utility space
     * Once it is retrieved, use the data provided from it in order to calculate data surrounding
     * the averages of the difference in bid utility
     * @return the estimated utility space calculated by the SocketParty super method
     */
    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        AbstractUtilitySpace space = super.estimateUtilitySpace();
        AbstractUtilitySpace realSpace = ((ExperimentalUserModel) userModel).getRealUtilitySpace();

        List<Bid> rankedBids = userModel.getBidRanking().getBidOrder();

        double diffSum = 0.0;
        int withinPointOne = 0;

        for(int i = 0; i < rankedBids.size(); i++) {
            double diff = Math.abs(space.getUtility(rankedBids.get(i)) - realSpace.getUtility(rankedBids.get(i)));
            System.out.println(i + ": ");
            System.out.println("Estimated: " + space.getUtility(rankedBids.get(i)));
            System.out.println("Real: " + realSpace.getUtility(rankedBids.get(i)));
            System.out.println("Difference: " + diff);
            System.out.println("----------------");

            diffSum += diff;

            if(diff <= 0.1) {
                withinPointOne++;
            }
        }

        diffSum = diffSum / rankedBids.size();

        System.out.println("Average Difference: " + diffSum);
        System.out.println("Percentage within 0.1 of real utility: " + (((1.0 * withinPointOne) / (1.0 * rankedBids.size())) * 100) + "%");

        return space;
    }
}