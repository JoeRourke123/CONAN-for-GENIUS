package socketparty;

import genius.core.Bid;
import genius.core.actions.Action;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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

        startConnection();
        String value = sendCommand("BLDMDL;;;" +
                "CON;;;100;;;200;;;ECON;;;" +
                "DIS;;;Value One;;;Value Two;;;Value Three;;;EDIS;;;" +
                "BID;;;150;;;Value Two;;;EBID;;;" +
                "BID;;;120;;;Value Three;;;EBID;;;" +
                "BID;;;110;;;Value One;;;EBID");
        System.out.println(value);
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
            String resp = in.readLine();
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
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public HashMap<String, String> negotiationEnded(Bid acceptedBid) {
        stopConnection();
        return super.negotiationEnded(acceptedBid);
    }
}
