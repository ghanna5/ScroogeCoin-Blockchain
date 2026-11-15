import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MaxFeeTxHandler finds a set of transactions with maximum total transaction fees.
 * i.e., maximizes sum over all transactions of (sum of input values - sum of output values).
 */
public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public boolean isValidTx(Transaction tx, UTXOPool pool) {
        Set<UTXO> claimedUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        List<Transaction.Input> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            if (!pool.contains(utxo)) return false;

            Transaction.Output prevOutput = pool.getTxOutput(utxo);
            byte[] message = tx.getRawDataToSign(i);
            if (!prevOutput.address.verifySignature(message, input.signature)) return false;

            if (claimedUTXOs.contains(utxo)) return false;
            claimedUTXOs.add(utxo);

            inputSum += prevOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) return false;
            outputSum += output.value;
        }

        return inputSum >= outputSum;
    }

    private double calcFee(Transaction tx, UTXOPool pool) {
        double inputSum = 0;
        double outputSum = 0;
        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (pool.contains(utxo)) {
                inputSum += pool.getTxOutput(utxo).value;
            }
        }
        for (Transaction.Output output : tx.getOutputs()) {
            outputSum += output.value;
        }
        return inputSum - outputSum;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // Use greedy approach: repeatedly pick the valid tx with highest fee
        List<Transaction> validTxs = new ArrayList<>();
        UTXOPool currentPool = new UTXOPool(utxoPool);
        List<Transaction> remaining = new ArrayList<>();
        for (Transaction tx : possibleTxs) remaining.add(tx);

        boolean changed = true;
        while (changed) {
            changed = false;
            Transaction bestTx = null;
            double bestFee = -1;

            for (Transaction tx : remaining) {
                if (isValidTx(tx, currentPool)) {
                    double fee = calcFee(tx, currentPool);
                    if (fee > bestFee) {
                        bestFee = fee;
                        bestTx = tx;
                    }
                }
            }

            if (bestTx != null) {
                validTxs.add(bestTx);
                remaining.remove(bestTx);
                changed = true;

                // Update pool
                for (Transaction.Input input : bestTx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    currentPool.removeUTXO(utxo);
                }
                byte[] txHash = bestTx.getHash();
                List<Transaction.Output> outputs = bestTx.getOutputs();
                for (int i = 0; i < outputs.size(); i++) {
                    currentPool.addUTXO(new UTXO(txHash, i), outputs.get(i));
                }
            }
        }

        // Update the handler's pool
        utxoPool = currentPool;
        return validTxs.toArray(new Transaction[0]);
    }
}
