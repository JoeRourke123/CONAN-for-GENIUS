package spearmanparty;

import genius.core.Bid;
import genius.core.utility.AbstractUtilitySpace;
import socketparty.SocketParty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        List<Bid> geniusRankedBids = userModel.getBidRanking().getBidOrder();

        List<Bid> z3RankedBids = new ArrayList<>();
        z3RankedBids.addAll(geniusRankedBids);      // Create a copy of the bid rankings
        // Use a comparator object to sort the bid objects by their respective utilities
        z3RankedBids.sort(new Comparator<Bid>() {
            @Override
            public int compare(Bid o1, Bid o2) {
                double uo1 = space.getUtility(o1);
                double uo2 = space.getUtility(o2);

                if(uo1 < uo2) {
                    return -1;
                } else if(uo1 > uo2) {
                    return 1;
                } return 0;
            }
        });

        // Print the calculated Spearman's Coefficient
        System.out.println("Spearman Coefficient: " + spearmanCoefficient(z3RankedBids, geniusRankedBids));

        return space;
    }

    /**
     * A list of ranked bids in order to calculate the Spearman's Coefficient of the two
     * rankings
     * @param listOne - a ranked list of bids, i.e. the estimated bid ranking
     * @param listTwo - a second ranked list of bids, i.e. the real bid ranking
     * @return
     */
    private double spearmanCoefficient(List<Bid> listOne, List<Bid> listTwo) {
        double d2Sum = 0;
        double n = listOne.size();

        // Calculate the sum of d^2 for each bid
        for(int i = 0; i < n; i++) {
            for(int j = 0; j < n; j++) {
                if(listOne.get(i).equals(listTwo.get(j))) {
                    d2Sum += Math.pow(Math.abs(i - j), 2);
                }
            }
        }

        // Do the Spearman's Coefficient calculation
        return 1 - ((6 * d2Sum) / (n * (Math.pow(n, 2) - 1)));
    }
}
