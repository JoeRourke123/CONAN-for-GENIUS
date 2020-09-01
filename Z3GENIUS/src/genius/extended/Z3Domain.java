package genius.extended;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.Objective;

import java.util.List;
import java.util.Random;

public class Z3Domain implements Domain {
    private List<Issue> issues;

    public Z3Domain(List<Issue> i) {
        issues = i;
    }

    @Override
    public List<Objective> getObjectives() {
        return null;
    }

    @Override
    public Objective getObjectivesRoot() {
        return null;
    }

    @Override
    public List<Issue> getIssues() {
        return issues;
    }

    @Override
    public Bid getRandomBid(Random r) {
        return null;
    }

    @Override
    public long getNumberOfPossibleBids() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }
}
