package z3;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.*;
import genius.extended.Z3Domain;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Model;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Z3Main {
    public static void main(String[] args) {
        ServerSocket server = null;
        try {
            server = new ServerSocket(102, 1);

            while (true) {
                Socket socket = server.accept();
                String[] returnData = {"ERR"};

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                    String[] data = in.readLine().split(";;;");

                    switch (data[0]) {
                        // For building a model given a domain and
                        case "BLDMDL":
                            returnData = buildModel(data);
                            break;
                        default:
                            throw new Z3ParseException();
                    }

                    String formatted = messageFormatter(returnData);
                    System.out.println(formatted);
                    writer.println(formatted);
                } catch (Z3ParseException z) {
                    System.err.println("The data provided was not valid");
                }

                socket.close();
            }
        } catch (IOException i) {
            System.err.println("There was an IOException...");
            System.err.println(i);
        } finally {
            try {
                if (server != null) {
                    server.close();
                }
            } catch (IOException e) {
                System.err.println("An error occurred while attempting to close the server");
            }
        }
    }

    public static String[] buildModel(String[] data) throws Z3ParseException {
        HashSet<String> possibleCommands = new HashSet<>(Arrays.asList("CON", "DIS", "BID"));
        Z3Solver z3;

        int modelAmount = 0;

        final List<Issue> issues = new ArrayList<>();
        List<Bid> bids = new ArrayList<>();

        for (int i = 1; i < data.length; i++) {
            String currentCommand = data[i];

            System.out.println("Issues Created: " + issues.size());
            System.out.println("Bids Created: " + bids.size());

            switch (currentCommand) {
                case "CON":
                    System.out.println("----\nContinuous Issue");
                    modelAmount += 4;

                    Issue intIssue = new IssueInteger(String.valueOf(issues.size()),
                            issues.size(),
                            Integer.parseInt(data[++i]),
                            Integer.parseInt(data[++i]));

                    issues.add(intIssue);
                    break;
                case "DIS":
                    List<String> values = new ArrayList<>();
                    System.out.println("----\nDiscrete Issue");
                    modelAmount++;

                    while (!data[i + 1].equals("EDIS")) {
                        values.add(data[++i]);
                        System.out.println(data[i]);

                        modelAmount++;
                    }

                    issues.add(new IssueDiscrete(
                            String.valueOf(issues.size()),
                            issues.size(),
                            values.toArray(new String[0])));
                    break;
                case "BID":
                    HashMap<Integer, Value> bidMap = new HashMap<>();
                    Domain domain = new Z3Domain(issues);

                    System.out.println("----\nBid");

                    modelAmount++;

                    for (Issue iss : issues) {
                        if (iss.getType() == ISSUETYPE.DISCRETE) {
                            bidMap.put(iss.getNumber(), new ValueDiscrete(data[++i]));
                        } else if (iss.getType() == ISSUETYPE.INTEGER) {
                            bidMap.put(iss.getNumber(), new ValueInteger(Integer.valueOf(data[++i])));
                        }
                        System.out.println(data[i]);
                    }
                    bids.add(new Bid(domain, bidMap));

                    break;
                default:
                    System.err.println("The provided command is not valid: " + currentCommand);
                    throw new Z3ParseException();
            }

            // Should close the currentCommand
            if (!data[++i].equals("E" + currentCommand)) {
                System.out.println("The closing command is not valid: " + data[i]);
                throw new Z3ParseException();
            }
        }

        z3 = new Z3Solver(
                bids,
                issues
        );

        List<Model.ValueAssignment> model = z3.estimate(bids, issues);

        List<String> returnData = new ArrayList<>();

        // Parse estimator results
        if (model != null && model.size() > 0) {
            returnData.add("MDL");

            try {
                Map<String, Double> mappedValues = new HashMap<>();
                List<String> weightings = new ArrayList<>();

                for (int i = 0; i < modelAmount; i++) {
                    Model.ValueAssignment va = model.get(i);
                    System.out.println(va.getName() + " - " + va.getValue());

                    mappedValues.put(va.getName(), ((Rational) va.getValue()).doubleValue());
                }

                for (int issueCount = 0; issueCount < issues.size(); issueCount++) {
                    Issue issue = issues.get(issueCount);
                    weightings.add(mappedValues.get("weight-" + issueCount).toString());

                    if (issue.getType() == ISSUETYPE.DISCRETE) {
                        returnData.add("DIS");
                        for (int valueCount = 0; valueCount < ((IssueDiscrete) issue).getNumberOfValues(); valueCount++) {
                            returnData.add(mappedValues.get("issue-" + issueCount + "-" + valueCount).toString());
                        }
                        returnData.add("EDIS");
                    } else if (issue.getType() == ISSUETYPE.INTEGER) {
                        returnData.add("CON");
                        returnData.add(mappedValues.get("issue-" + issueCount + "-minutil").toString());
                        returnData.add(mappedValues.get("issue-" + issueCount + "-maxutil").toString());
                        returnData.add(mappedValues.get("issue-" + issueCount + "-slope").toString());
                        returnData.add("ECON");
                    }
                }

                returnData.add("WHT");
                returnData.addAll(weightings);
                returnData.add("EWHT");

            } catch (Exception | Error e) {
                System.err.println("Something went wrong!");
                System.err.println(e);
            }
        } else {
            returnData.add("ERR");
        }


        return returnData.toArray(new String[0]);
    }

    private static String messageFormatter(String[] data) {
        StringBuilder ss = new StringBuilder();

        for (int i = 0; i < data.length; i++) {
            ss.append(data[i]);

            if (i < data.length - 1) {
                ss.append(";;;");
            }
        }

        return ss.toString();
    }
}
