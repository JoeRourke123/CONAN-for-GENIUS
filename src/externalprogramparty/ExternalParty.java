package externalprogramparty;

import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class ExternalParty extends AbstractNegotiationParty {
    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        try {
            System.out.println(System.getProperty("user.dir"));
            Process process = new ProcessBuilder("java", "--version").start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } catch (IOException i) {
            System.err.println("IOException occurred");
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        return new Offer(getPartyId(), generateRandomBid());
    }

    @Override
    public String getDescription() {
        return "Launches an external program on initialisation";
    }
}
