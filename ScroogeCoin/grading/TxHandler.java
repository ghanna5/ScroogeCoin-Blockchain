import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is {@code utxoPool}.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to
     *     the sum of its output values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        Set<UTXO> claimedUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        List<Transaction.Input> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) Check UTXO is in pool
            if (!utxoPool.contains(utxo)) {
                return false;
            }

            // (2) Verify signature
            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);
            byte[] message = tx.getRawDataToSign(i);
            if (!prevOutput.address.verifySignature(message, input.signature)) {
                return false;
            }

            // (3) No double spending within this transaction
            if (claimedUTXOs.contains(utxo)) {
                return false;
            }
            claimedUTXOs.add(utxo);

            inputSum += prevOutput.value;
        }

        // (4) All output values non-negative
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }

        // (5) Input sum >= output sum
        return inputSum >= outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed
     * transactions, checking each transaction for correctness, returning
     * a mutually valid array of accepted transactions, and updating the
     * current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTxs = new ArrayList<>();
        boolean changed = true;

        // Keep iterating until no new valid transactions can be added
        // (handles dependencies within the same block)
        Set<Transaction> remaining = new HashSet<>();
        for (Transaction tx : possibleTxs) {
            remaining.add(tx);
        }

        while (changed) {
            changed = false;
            Set<Transaction> toRemove = new HashSet<>();
            for (Transaction tx : remaining) {
                if (isValidTx(tx)) {
                    validTxs.add(tx);
                    toRemove.add(tx);
                    changed = true;

                    // Remove spent UTXOs
                    for (Transaction.Input input : tx.getInputs()) {
                        UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                        utxoPool.removeUTXO(utxo);
                    }

                    // Add new UTXOs from this transaction
                    byte[] txHash = tx.getHash();
                    List<Transaction.Output> outputs = tx.getOutputs();
                    for (int i = 0; i < outputs.size(); i++) {
                        UTXO utxo = new UTXO(txHash, i);
                        utxoPool.addUTXO(utxo, outputs.get(i));
                    }
                }
            }
            remaining.removeAll(toRemove);
        }

        return validTxs.toArray(new Transaction[0]);
    }
}
