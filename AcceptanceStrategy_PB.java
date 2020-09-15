import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.javatuples.Quintet;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OfferingStrategy;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
/**
 * 
 * @author Pallavi Bagga
 *
 * @institution Royal Holloway, University of London
 */

public class DH_AcceptanceStrategy extends AcceptanceStrategy {

	//private double thresholdUtility = 0.95; //Later we use ANEGMA to find the dynamic threshold utility
	private double fixedUtility = 0.98;
	private double dynamicUtility = 1D;
	private double reservationThresholdUtility = 0.85;
	private Quintet newQuintet = null;
	private Quintet currentQuintet = null;
	private double time;
	private static Actions outputAction = null;

	private long totalNumberOfPossibleBids;
	private int numberOfIssues;
	private int givenNumberOfbids;
	private double recentlyReceivedBidUtility;
	private double opponentBestBidUtility;
	private double averageOpponentUtility;
	private double standardDevUtility;

	private double myFutureBidUtility;
	private double lastReceivedBidUtility;

	ConcurrentHashMap<Integer, List<Quintet>> regressionQuintetMap = new ConcurrentHashMap<Integer, List<Quintet>>();
	Quintet acceptQuintet = null;
	
	public DH_AcceptanceStrategy(NegotiationSession negotiationSession, OfferingStrategy offeringStrategy) {
		this.negotiationSession = negotiationSession;
		this.offeringStrategy = offeringStrategy;
	}

	@Override
	public Actions determineAcceptability(){

		System.out.println("I am going to check whether to accept or not");
		roundCounter++;
		time = negotiationSession.getTime();
		DH_TargetUtility t_u = new DH_TargetUtility(negotiationSession, time);
		
		if (time >= 0.7) {
			try {
				dynamicUtility = t_u.calculateTargetValue();
				System.out.println("at time t = "+time + "the dynamic threshold utility is: "+dynamicUtility);
				if(dynamicUtility < reservationThresholdUtility) dynamicUtility = reservationThresholdUtility;
			}
			catch (Exception e) {
				e.printStackTrace();
			} 
		}
		myFutureBidUtility = offeringStrategy.getNextBid().getMyUndiscountedUtil();
		lastReceivedBidUtility = negotiationSession.getOpponentBidHistory().getLastBidDetails().getMyUndiscountedUtil();

		BidHistory opponentBidHistorySortedToUtility = negotiationSession.getOpponentBidHistory().sortToUtility();
		
		//Let's change the percentile linearly with time using point-slope formula
		//t1,q1 = 0.4,1.0 and t2,q2 = 0.8, 0.6, therefore, slope = -0.2
		//y-y1 = m *(x -x1) implies y = m * (x - x1) + y1
		
		double q = (-0.2)*(time - 0.8) + 0.6;
		
    //the following code can be optimized 
		if((time < 0.4 && lastReceivedBidUtility >= fixedUtility) 
				||
				(time >= 0.4 && time < 0.5 && lastReceivedBidUtility >= myFutureBidUtility &&  lastReceivedBidUtility > bestOfQquantileBids(opponentBidHistorySortedToUtility, q))
				//why dynamic threshold is not considered here.. because my future bid utility will always be higher than that my threshold value
				||
				(time >= 0.5 && time < 0.6 && lastReceivedBidUtility >= myFutureBidUtility &&  lastReceivedBidUtility > bestOfQquantileBids(opponentBidHistorySortedToUtility, q))
				//best out of the bottom 90% of the received bids in the ordered utility from high to less
				||
				(time >= 0.6 && time < 0.7 && lastReceivedBidUtility >= myFutureBidUtility && lastReceivedBidUtility > bestOfQquantileBids(opponentBidHistorySortedToUtility, q))
				//best out of the bottom 80% of the received bids
				||
				(time >= 0.7 && time < 0.8 && lastReceivedBidUtility >= dynamicUtility &&  lastReceivedBidUtility > bestOfQquantileBids(opponentBidHistorySortedToUtility, q))
				//best out of the bottom 70% of the bids
				||
				(time >= 0.8 && time < 0.9 && lastReceivedBidUtility >= dynamicUtility && lastReceivedBidUtility > bestOfQquantileBids(opponentBidHistorySortedToUtility, q))
				//best out of the bottom 60% of the bids 
				||
				(time >= 0.9 && lastReceivedBidUtility >= dynamicUtility)) 
		{
			outputAction  = Actions.Accept;
		}
		else
		{
			System.out.println("I am going to reject the received offer and propose a new offer");
			outputAction  = Actions.Reject;
		}	
			return outputAction;
	}


	private double bestOfQquantileBids(BidHistory opponentSortedHistory, double q) {
		// TODO Auto-generated method stub
		//this oppoenent history is sorted in decreasing order based on utility values
		double util = 0D;
		int k = (int) (Math.ceil((1-q) * opponentSortedHistory.size()) - 1);
		util = opponentSortedHistory.getHistory().get(k).getMyUndiscountedUtil();
		//System.out.println("Q = "+q+" size = " + opponentSortedHistory.size() + " index = "+ k + " util = " + util);
		return util;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Dice Haggler's Acceptance Startegy";
	}

}
