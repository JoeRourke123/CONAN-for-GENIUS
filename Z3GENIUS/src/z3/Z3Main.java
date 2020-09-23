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
    /**
     * The main thread of the socket server - listens for incoming connections, parses the message and sends it
     * to the correct util function to act upon the message headers. This is the method which is run by the jar file.
     *
     * @param args - no args are provided to the program
     */
    public static void main(String[] args) {
        ServerSocket server = null;
        try {
            // Set up the server on port 102 - only allowing 1 connection at the time currently.
            server = new ServerSocket(10211, 1);

            // An infinite loop which will continually accept connections when data is provided
            while (true) {
                Socket socket = server.accept();
                String[] returnData = {"ERR"};

                // Open a reader and writer to receive and send data between the server and client socket connection
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                    // Split the data by the protocol separator, three semi-colons
                    String[] data = in.readLine().split(";;;");

                    // In the future more header opcodes may be used, hence the switch statement
                    switch (data[0]) {
                        // For building a model given a domain and bids
                        case "BLDMDL":
                            returnData = buildModel(data);
                            break;
                        default:
                            throw new Z3ParseException();
                    }

                    // Format the message back into the readable string format
                    String formatted = messageFormatter(returnData);
                    System.out.println(formatted);

                    // Write it back to the client
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
            // Once the loop breaks, be sure to close the server so the port may be freed for future runs
            try {
                if (server != null) {
                    server.close();
                }
            } catch (IOException e) {
                System.err.println("An error occurred while attempting to close the server");
            }
        }
    }

    /**
     * This method uses the Z3Solver class in order to build a model which is able to solve the constraints provided by
     * the client - this allows the GENIUS agent to estimate utilities
     *
     * @param data - the data which was sent to the server by the client - hopefully consisting of protocol compliant opcodes and data
     * @return - a list of opcodes and data to be formatted before sending back to the client
     * @throws Z3ParseException - for the situation where the data provided to the server may be incorrectly formatted
     */
    private static String[] buildModel(String[] data) throws Z3ParseException {
        HashSet<String> possibleCommands = new HashSet<>(Arrays.asList("CON", "DIS", "BID", "BND"));
        Z3Solver z3;

        double lowBound = 0.0, highBound = 1.0;

        // This counter is used later for the parsing of results from the model
        int modelAmount = 0;

        // Use the GENIUS classes to parse messages from the client into, easier than creating new methods
        final List<Issue> issues = new ArrayList<>();
        List<Bid> bids = new ArrayList<>();

        for (int i = 1; i < data.length; i++) {
            String currentCommand = data[i];

            System.out.println("Issues Created: " + issues.size());
            System.out.println("Bids Created: " + bids.size());

            switch (currentCommand) {
                case "CON":
                    System.out.println("----\nContinuous Issue");
                    // Each continuous issue creates 4 values in the model (min util, max util, weight, and slope)
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
                    // Each discrete issue creates (no. of values) + 1 model values which need to be fetched
                    modelAmount++;

                    // Loop through each provided value and add it to the discrete issue value list
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
                    // This code parses in each of the provided bids, ordered from lowest to highest
                    HashMap<Integer, Value> bidMap = new HashMap<>();
                    Domain domain = new Z3Domain(issues);   // A special Z3Domain is created as the default one is package private
                    // so cannot be instantiated here
                    System.out.println("----\nBid");

                    modelAmount++;  // Each bid creates one model value, it's utility

                    for (Issue iss : issues) {
                        // Parse the bids into a map of each value to the issue number ID
                        if (iss.getType() == ISSUETYPE.DISCRETE) {
                            bidMap.put(iss.getNumber(), new ValueDiscrete(data[++i]));
                        } else if (iss.getType() == ISSUETYPE.INTEGER) {
                            bidMap.put(iss.getNumber(), new ValueInteger(Integer.valueOf(data[++i])));
                        }
                        System.out.println(data[i]);
                    }
                    bids.add(new Bid(domain, bidMap));

                    break;
                case "BND":
                    lowBound = Double.parseDouble(data[++i]);
                    highBound = Double.parseDouble(data[++i]);
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

        // Initialise the Z3Solver class, passing in the parsed issues and bid values
        z3 = new Z3Solver(
                bids,
                issues
        );

        // Get the model values from the constraints applied in the estimate method
        List<Model.ValueAssignment> model = z3.estimate(bids, issues, lowBound, highBound);

        List<String> returnData = new ArrayList<>();

        // Parse estimator results
        if (model != null && model.size() > 0) {
            returnData.add("MDL");

            Map<String, Double> mappedValues = new HashMap<>();
            List<String> weightings = new ArrayList<>();

            // This code only finds the useful values generated by the model
            // Looping through all the model values caused a fatal error in the JRE
            // Hence, the modelAmount variable is used to limit the iterations
            for (int i = 0; i < modelAmount; i++) {
                Model.ValueAssignment va = model.get(i);
                System.out.println(va.getName() + " - " + va.getValue());

                mappedValues.put(va.getName(), ((Rational) va.getValue()).doubleValue());
            }

            // Loop through the ID of each issue in the issue list
            for (int issueCount = 0; issueCount < issues.size(); issueCount++) {
                Issue issue = issues.get(issueCount);

                // Add the estimated weighting value to the weightings array
                weightings.add(mappedValues.get("weight-" + issueCount).toString());

                // If the current issue is a discrete type
                if (issue.getType() == ISSUETYPE.DISCRETE) {
                    // Indicate that this is the start of a discrete issue in the protocol format
                    returnData.add("DIS");

                    // For each discrete value, add the estimated utility/preference of that value to the return message
                    for (int valueCount = 0; valueCount < ((IssueDiscrete) issue).getNumberOfValues(); valueCount++) {
                        returnData.add(mappedValues.get("issue-" + issueCount + "-" + valueCount).toString());
                    }
                    returnData.add("EDIS");
                } else if (issue.getType() == ISSUETYPE.INTEGER) {
                    // Indicate that this is the start of a continuous issue in the protocol format
                    returnData.add("CON");      // Add the required values for the GENIUS utility space (the min and max utilities)
                    returnData.add(mappedValues.get("issue-" + issueCount + "-minutil").toString());
                    returnData.add(mappedValues.get("issue-" + issueCount + "-maxutil").toString());
                    returnData.add("ECON");
                    // Close the continuous issue
                }
            }

            // Finally, add the weights of each issue to the message
            returnData.add("WHT");
            returnData.addAll(weightings);
            returnData.add("EWHT");
        } else {
            // If there is a problem with the generated model, just send the protocol's error operation
            returnData.add("ERR");
        }

        return returnData.toArray(new String[0]);       // Converts the arraylist to a string array
    }

    /**
     * Given the appropriate data will join each item, separated by the protocol separator symbols (three semi colons)
     * @param data - the data to parse into a sendable socket message
     * @return - the joined data opcodes and data into a single string
     */
    private static String messageFormatter(String[] data) {
        StringBuilder ss = new StringBuilder();     // Uses a stringbuilder for optimisation purposes

        for (int i = 0; i < data.length; i++) {
            ss.append(data[i]);

            if (i < data.length - 1) {
                ss.append(";;;");
            }
        }

        return ss.toString();
    }
}
