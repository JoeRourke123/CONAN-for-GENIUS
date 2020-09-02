package socketparty;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.*;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.EvaluatorInteger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SocketParty extends AbstractNegotiationParty {
    private Process process;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

//        try {
//            process = new ProcessBuilder("java", "-jar", "artifacts/Z3GENIUS_jar/Z3GENIUS.jar").start();
//        } catch (IOException i) {
//            System.err.println("IOException occurred");
//        }
    }

    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        startConnection();
        String modelRequest = getModelRequest();

        try {
            String[] data = sendCommand(modelRequest).split(";;;");

            int issueIndex = 0;
            AdditiveUtilitySpace utilSpace = new AdditiveUtilitySpace(getDomain());

            if (data[0].equals("MDL")) {
                for (int i = 1; i < data.length; i++) {
                    String currentCommand = data[i];

                    switch (currentCommand) {
                        case "CON":
                            EvaluatorInteger evalCon = new EvaluatorInteger();

                            evalCon.setLinearFunction(Double.valueOf(data[++i]), Double.valueOf(data[++i]));

                            utilSpace.addEvaluator(getDomain().getIssues().get(issueIndex++), evalCon);
                            break;
                        case "DIS":
                            EvaluatorDiscrete evalDis = new EvaluatorDiscrete();
                            IssueDiscrete issueDis = (IssueDiscrete) getDomain().getIssues().get(issueIndex++);
                            int valueIndex = 0;

                            while(!data[i + 1].equals("EDIS")) {
                                evalDis.setEvaluationDouble(
                                    issueDis.getValue(valueIndex++),
                                    Double.valueOf(data[++i])
                                );
                            }

                            utilSpace.addEvaluator(issueDis, evalDis);
                            break;
                        case "WHT":
                            if(issueIndex > 0) {
                                issueIndex = 0;
                            }

                            while(!data[i + 1].equals("EWHT")) {
                                utilSpace.setWeight(
                                        getDomain().getIssues().get(issueIndex++),
                                        Double.valueOf(data[++i]));
                            }
                            break;
                        default:
                            System.err.println("The provided command is not valid: " + currentCommand);
                            throw new NullPointerException();
                    }

                    // Should close the currentCommand
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
            return null;
        }
    }

    private void startConnection() {
        try {
            socket = new Socket("127.0.0.1", 102);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("There was an exception when opening the socket");
        }
    }

    private String sendCommand(String msg) {
        try {
            out.println(msg);
            String resp;
            while ((resp = in.readLine()) == null) ;

            return resp;
        } catch (IOException i) {
            System.err.println("An error occured while reading and writing from the socket");
        }

        return null;
    }

    private void stopConnection() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException i) {
            System.err.println("There was an error when closing the sockets");
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        return new Offer(getPartyId(), generateRandomBid());
    }

    @Override
    public String getDescription() {
        return "Connects to Z3 solver over a socket connection";
    }

    @Override
    public HashMap<String, String> negotiationEnded(Bid acceptedBid) {
//        if(hasPreferenceUncertainty()) {
//            stopConnection();
//        }
        return super.negotiationEnded(acceptedBid);
    }

    public String getModelRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append("BLDMDL;;;");

        for (Issue issue : getDomain().getIssues()) {
            if (issue.getType() == ISSUETYPE.DISCRETE) {
                sb.append("DIS;;;");

                for (ValueDiscrete val : ((IssueDiscrete) issue).getValues()) {
                    sb.append(val.getValue());
                    sb.append(";;;");
                }

                sb.append("EDIS;;;");
            } else if (issue.getType() == ISSUETYPE.INTEGER) {
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

            if (bidIndex < userModel.getBidRanking().getSize() - 1) {
                sb.append("EBID;;;");
            } else {
                sb.append("EBID");
            }
        }

        return sb.toString();
    }
}
