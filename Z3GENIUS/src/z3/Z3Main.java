package z3;

import com.sun.jdi.IntegerValue;
import com.sun.tools.jdi.IntegerValueImpl;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.DomainImpl;
import genius.core.analysis.pareto.IssueValue;
import genius.core.issue.*;
import genius.core.utility.AdditiveUtilitySpace;
import genius.extended.Z3Domain;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.NumeralFormula;

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
                    String[] data = in.readLine().split(";;");

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
                if(server != null) {
                    server.close();
                }
            } catch(IOException e) {
                System.err.println("An error occurred while attempting to close the server");
            }
        }
    }

    public static String[] buildModel(String[] data) throws Z3ParseException {
        HashSet<String> possibleCommands = new HashSet<>(Arrays.asList("CON", "DIS", "BID"));
        Z3Solver z3;

        final List<Issue> issues = new ArrayList<>();
        List<Bid> bids = new ArrayList<>();

        z3 = new Z3Solver(
            bids,
            issues
        );

        for(int i = 1; i < data.length; i++) {
            String currentCommand = data[i];

            switch(currentCommand) {
                case "CON":
                    Issue intIssue = new IssueInteger(String.valueOf(issues.size()),
                            issues.size(),
                            Integer.parseInt(data[++i]),
                            Integer.parseInt(data[++i]));

                    issues.add(intIssue);
                    break;
                case "DIS":
                    List<String> values = new ArrayList<>();

                    while(!data[i + 1].equals("EDIS")) {
                        values.add(data[++i]);
                    }

                    issues.add(new IssueDiscrete(
                            String.valueOf(issues.size()),
                            issues.size(),
                            values.toArray(new String[0])));
                    break;
                case "BID":
                    HashMap<Integer, Value> bidMap = new HashMap<>();
                    Domain domain = new Z3Domain(issues);

                    for(Issue iss : issues) {
                        if(iss.getType() == ISSUETYPE.DISCRETE) {
                            bidMap.put(iss.getNumber(), new ValueDiscrete(data[++i]));
                        } else if(iss.getType() == ISSUETYPE.INTEGER) {
                            bidMap.put(iss.getNumber(), new ValueInteger(Integer.valueOf(data[++i])));
                        }
                    }
                    bids.add(new Bid(domain, bidMap));

                    break;
                default:
                    throw new Z3ParseException();
            }

            // Should close the currentCommand
            if(!data[++i].equals("E" + currentCommand)) {
                throw new Z3ParseException();
            }
        }

        Model model = z3.estimate(bids, issues);

        List<String> returnData = new ArrayList<>();

        // Parse estimator results
        if(model != null) {
            for(Model.ValueAssignment v : model.asList()) {
                System.out.println(v.getName() + " - " + v.getValue().toString());
            }
        } else {
            returnData.add("ERR");
        }

        return returnData.toArray(new String[0]);
    }

    public static String messageFormatter(String[] data) {
        StringBuilder ss = new StringBuilder();

        for(String item : data) {
            ss.append(item);
            ss.append(";;");
        }

        return ss.toString();
    }
}
