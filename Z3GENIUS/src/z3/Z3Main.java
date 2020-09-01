package z3;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.*;
import genius.extended.Z3Domain;
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

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String[] data = in.readLine().split(";;;");

                    switch (data[0]) {
                        // For building a model given a domain and
                        case "BLDMDL":
                            returnData = buildModel(data);
                        default:
                            throw new Z3ParseException();
                    }
                } catch (Z3ParseException z) {
                    System.err.println("The data provided was not valid");
                }

                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true);

                writer.println(messageFormatter(returnData));
            }
        } catch (IOException i) {
            System.err.println("There was an IOException...");
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
        if (model != null) {
            try {
                for (int i = 0; i < modelAmount; i++) {
                    Model.ValueAssignment va = model.get(i);
                    System.out.println(va.getName() + " - " + va.getValue());
                }
            } catch(Exception | Error e) {
                System.err.println("Something went wrong!");
                System.err.println(e);
            }
        } else {
            returnData.add("ERR");
        }

        try {
            z3.close();
        } catch(Error | Exception e) {
            System.err.println("Something went wrong while closing the solver");
            System.err.println(e);
        }

        return returnData.toArray(new String[0]);
    }

    public static String messageFormatter(String[] data) {
        StringBuilder ss = new StringBuilder();

        for (String item : data) {
            ss.append(item);
            ss.append(";;");
        }

        return ss.toString();
    }
}
