package z3;

import org.sosy_lab.java_smt.api.NumeralFormula;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

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

        List<NumeralFormula.RationalFormula> weightings = new ArrayList<>();
        List<List<NumeralFormula.RationalFormula>> issues = new ArrayList<>();
        List<List<NumeralFormula.RationalFormula>> bidValues = new ArrayList<>();
        List<String> issueTypes = new ArrayList<>();

        for(int i = 1; i < data.length; i++) {
            String currentCommand = data[i];
            String item = data[++i];

            if(possibleCommands.contains(currentCommand) && !currentCommand.equals("BID")) {
                issueTypes.add(currentCommand);
            }

            while(!item.equals("E" + currentCommand)) {
                if(possibleCommands.contains(item)) {
                    throw new Z3ParseException();
                }

                switch(currentCommand) {
                    case "CON":
                        break;
                    case "DIS":
                        break;
                    case "BID":
                        break;
                    default:
                        throw new Z3ParseException();
                }

                item = data[++i];
            }
        }

        z3 = new Z3Solver(
            weightings,
            issues,
            bidValues,
            issueTypes.toArray(new String[0])
        );
        z3.estimate();

        List<String> returnData = new ArrayList<>();

        // Parse estimator results

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
