package utils;

import genius.core.Bid;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Spearman {
    public static double coefficient(AbstractUtilitySpace estimated, UserModel userModel) {
        List<Bid> geniusRankedBids = userModel.getBidRanking().getBidOrder();

        List<Bid> z3RankedBids = new ArrayList<>();
        z3RankedBids.addAll(geniusRankedBids);      // Create a copy of the bid rankings
        // Use a comparator object to sort the bid objects by their respective utilities
        z3RankedBids.sort(new Comparator<Bid>() {
            @Override
            public int compare(Bid o1, Bid o2) {
                double uo1 = estimated.getUtility(o1);
                double uo2 = estimated.getUtility(o2);

                if (uo1 < uo2) {
                    return -1;
                } else if (uo1 > uo2) {
                    return 1;
                }
                return 0;
            }
        });

        return calcCoefficient(z3RankedBids, geniusRankedBids);
    }

    /**
     * A list of ranked bids in order to calculate the Spearman's Coefficient of the two
     * rankings
     *
     * @param listOne - a ranked list of bids, i.e. the estimated bid ranking
     * @param listTwo - a second ranked list of bids, i.e. the real bid ranking
     * @return
     */
    private static double calcCoefficient(List<Bid> listOne, List<Bid> listTwo) {
        double d2Sum = 0;
        double n = listOne.size();

        // Calculate the sum of d^2 for each bid
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (listOne.get(i).equals(listTwo.get(j))) {
                    d2Sum += Math.pow(Math.abs(i - j), 2);
                }
            }
        }

        // Do the Spearman's Coefficient calculation
        return 1 - ((6 * d2Sum) / (n * (Math.pow(n, 2) - 1)));
    }
}
