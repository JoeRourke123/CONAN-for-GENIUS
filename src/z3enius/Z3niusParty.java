package z3enius;

import genius.core.Bid;
import genius.core.issue.*;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.EvaluatorInteger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;

public abstract class Z3niusParty extends AbstractNegotiationParty {
    protected Socket socket;
    protected BufferedReader in;
    protected PrintWriter out;

    /**
     * estimateUtilitySpace (overridden from the GENIUS class)
     * - Connects to the Z3GENIUS program and sends a message containing the available data to the agent on the
     * domain and the preference bids
     * - Waits for a response from the server and then parses these values into a utility space class
     * - Only run if the agent is not provided with the full preference profile
     */
    public AbstractUtilitySpace estimateUtilitySpace() {
        System.out.println("Starting Connection");
        startConnection();
        System.out.println("Connection Started");
        String modelRequest = getModelRequest();
        System.out.println("Model Request Built");
        try {
            String[] data = sendCommand(modelRequest).split(";;;");
            System.out.println("Message Sent");

            int issueIndex = 0;
            AdditiveUtilitySpace utilSpace = new AdditiveUtilitySpace(getDomain());

            // Checks that the response is actually model information based on my protocol, otherwise null is returned
            if (data[0].equals("MDL")) {
                for (int i = 1; i < data.length; i++) {
                    String currentCommand = data[i];

                    switch (currentCommand) {
                        // Checks if data is the estimated continuous values
                        case "CON":
                            EvaluatorInteger evalCon = new EvaluatorInteger();

                            // Builds an evaluator object using the minimum and maximum possible utilties
                            evalCon.setLinearFunction(Double.valueOf(data[++i]), Double.valueOf(data[++i]));

                            utilSpace.addEvaluator(getDomain().getIssues().get(issueIndex++), evalCon);
                            break;
                        case "DIS":
                            EvaluatorDiscrete evalDis = new EvaluatorDiscrete();
                            IssueDiscrete issueDis = (IssueDiscrete) getDomain().getIssues().get(issueIndex++);
                            int valueIndex = 0;

                            while (!data[i + 1].equals("EDIS")) {
                                // Adds an evaluation for each possible value in the discrete issue
                                evalDis.setEvaluationDouble(
                                        issueDis.getValue(valueIndex++),
                                        Double.valueOf(data[++i])
                                );
                            }

                            utilSpace.addEvaluator(issueDis, evalDis);
                            break;
                        case "WHT":
                            if (issueIndex > 0) {
                                issueIndex = 0;
                            }

                            // Adds the weights of each issue to the utility space also
                            while (!data[i + 1].equals("EWHT")) {
                                utilSpace.setWeight(
                                        getDomain().getIssues().get(issueIndex++),
                                        Double.valueOf(data[++i]));
                            }
                            break;
                        default:
                            System.err.println("The provided command is not valid: " + currentCommand);
                            throw new NullPointerException();
                    }

                    // Should close the currentCommand or throw an error if the message is invalid
                    if (!data[++i].equals("E" + currentCommand)) {
                        System.out.println("The closing command is not valid: " + data[i]);
                        throw new NullPointerException();
                    }
                }

                return utilSpace;
            } else {
                throw new NullPointerException();
            }
        } catch (NullPointerException n) {
            System.err.println("The message received was not valid");
            return null;
        }
    }

    /**
     * Opens a connection to the Z3GENIUS program over a socket, and uses readers and writers to
     * send and receive data from the respective in/out streams
     */
    private void startConnection() {
        try {
            socket = new Socket("127.0.0.1", 102);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("There was an exception when opening the socket");
        }
    }

    /**
     * Sends the msg parameter over the socket to the Z3GENIUS program
     *
     * @param msg - a string message, written in the custom protocol, to be sent
     * @return - the response from the server, synchronously waited for using a loop
     */
    private String sendCommand(String msg) {
        try {
            System.out.println(msg);
            out.println(msg);
            String resp;
            // Keep checking if the server responds until it does
            while ((resp = in.readLine()) == null) ;

            return resp;
        } catch (IOException i) {
            System.err.println("An error occured while reading and writing from the socket");
        }

        return null;
    }

    /**
     * Gets a random value for the given issue.
     *
     * @param currentIssue The issue to generate a random value for
     * @return The random value generated for the issue
     * @throws Exception if the issues type is not Discrete, Real or Integer.
     */
    protected Value getRandomValue(Issue currentIssue) throws Exception {

        Value currentValue;
        int index;

        switch (currentIssue.getType()) {
            case DISCRETE:
                IssueDiscrete discreteIssue = (IssueDiscrete) currentIssue;
                index = (rand.nextInt(discreteIssue.getNumberOfValues()));
                currentValue = discreteIssue.getValue(index);
                break;
            case REAL:
                IssueReal realIss = (IssueReal) currentIssue;
                index = rand.nextInt(realIss.getNumberOfDiscretizationSteps()); // check
                // this!
                currentValue = new ValueReal(
                        realIss.getLowerBound() + (((realIss.getUpperBound() - realIss.getLowerBound()))
                                / (realIss.getNumberOfDiscretizationSteps())) * index);
                break;
            case INTEGER:
                IssueInteger integerIssue = (IssueInteger) currentIssue;
                index = rand.nextInt(integerIssue.getUpperBound() - integerIssue.getLowerBound() + 1);
                currentValue = new ValueInteger(integerIssue.getLowerBound() + index);
                break;
            default:
                throw new Exception("issue type " + currentIssue.getType() + " not supported");
        }

        return currentValue;
    }

    /**
     * Builds a readable message in the custom protocol to be sent to the Z3GENIUS server
     * Contains the issues in the domain, as well as the bids provided by GENIUS in the case of uncertainty
     *
     * @return - a string correctly formatted and parsable by the server
     */
    public String getModelRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append("BLDMDL;;;");

        for (Issue issue : getDomain().getIssues()) {
            if (issue.getType() == ISSUETYPE.DISCRETE) {
                System.out.println("Discrete Issue");
                sb.append("DIS;;;");

                for (ValueDiscrete val : ((IssueDiscrete) issue).getValues()) {
                    sb.append(val.getValue());
                    sb.append(";;;");
                }

                sb.append("EDIS;;;");
            } else if (issue.getType() == ISSUETYPE.INTEGER) {
                System.out.println("Integer Issue");
                sb.append("CON;;;");
                sb.append(((IssueInteger) issue).getLowerBound());
                sb.append(";;;");
                sb.append(((IssueInteger) issue).getUpperBound());
                sb.append(";;;ECON;;;");
            }
        }

        for (int bidIndex = 0; bidIndex < userModel.getBidRanking().getSize(); bidIndex++) {
            Bid bid = userModel.getBidRanking().getBidOrder().get(bidIndex);
            HashMap<Integer, Value> bidValues = bid.getValues();

            sb.append("BID;;;");
            for (int issueIndex = 1; issueIndex <= getDomain().getIssues().size(); issueIndex++) {
                if (getDomain().getIssues().get(issueIndex - 1).getType() == ISSUETYPE.DISCRETE) {
                    sb.append(((ValueDiscrete) bidValues.get(issueIndex)).getValue());
                } else if (getDomain().getIssues().get(issueIndex - 1).getType() == ISSUETYPE.INTEGER) {
                    sb.append(((ValueInteger) bidValues.get(issueIndex)).getValue());
                }

                sb.append(";;;");
            }

            sb.append("EBID;;;");
        }

        sb.append("BND;;;");
        sb.append(userModel.getBidRanking().getLowUtility());
        sb.append(";;;");
        sb.append(userModel.getBidRanking().getHighUtility());
        sb.append(";;;EBND");

        return sb.toString();
    }
}
