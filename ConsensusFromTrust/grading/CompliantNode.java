import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private int numRounds;
    private int currentRound;
    private boolean[] followees;

    // All transactions this node has ever seen (initial + received)
    private Set<Transaction> knownTransactions;

    // Track how many rounds each followee has been silent (sent nothing)
    private Map<Integer, Integer> silentRounds;

    // Transactions seen in the PREVIOUS round (to detect newly active followees)
    private Set<Integer> activeFollowees;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.numRounds = numRounds;
        this.currentRound = 0;
        this.knownTransactions = new HashSet<>();
        this.silentRounds = new HashMap<>();
        this.activeFollowees = new HashSet<>();
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        // Initialize silence tracking for all followees
        for (int i = 0; i < followees.length; i++) {
            if (followees[i]) {
                silentRounds.put(i, 0);
            }
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        knownTransactions.addAll(pendingTransactions);
    }

    // Return all known transactions each round so followers can learn about them.
    // After the final round this also serves as the consensus output.
    public Set<Transaction> getProposals() {
        currentRound++;
        return new HashSet<>(knownTransactions);
    }

    public void receiveCandidates(ArrayList<Integer[]> candidates) {
        // Track which followees sent something this round
        Set<Integer> sendersThisRound = new HashSet<>();

        for (Integer[] candidate : candidates) {
            int txId     = candidate[0];
            int senderId = candidate[1];

            // Accept any transaction from a trusted followee.
            // Ignore if sender is not actually a followee (shouldn't happen per simulation,
            // but defensive check).
            if (!followees[senderId]) continue;

            knownTransactions.add(new Transaction(txId));
            sendersThisRound.add(senderId);
        }

        // Update silence counters: followees that sent nothing this round get +1,
        // those that sent something reset to 0.
        for (int i = 0; i < followees.length; i++) {
            if (!followees[i]) continue;
            if (sendersThisRound.contains(i)) {
                silentRounds.put(i, 0);
            } else {
                silentRounds.put(i, silentRounds.get(i) + 1);
            }
        }

        activeFollowees = sendersThisRound;
    }
}
