import java.util.ArrayList;
import java.util.HashMap;

/* Block Chain should maintain only limited block nodes to satisfy the functions
   You should not have the all the blocks added to the block chain in memory 
   as it would overflow memory
 */

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   // all information required in handling a block in block chain
   private class BlockNode {
      public Block b;
      public BlockNode parent;
      public ArrayList<BlockNode> children;
      public int height;
      // utxo pool for making a new block on top of this block
      private UTXOPool uPool;
      private long timestamp;

      public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
         this.b = b;
         this.parent = parent;
         children = new ArrayList<BlockNode>();
         this.uPool = uPool;
         this.timestamp = System.currentTimeMillis();
         if (parent != null) {
            height = parent.height + 1;
            parent.children.add(this);
         } else {
            height = 1;
         }
      }

      public UTXOPool getUTXOPoolCopy() {
         return new UTXOPool(uPool);
      }
   }

   private HashMap<ByteArrayWrapper, BlockNode> blockMap;
   private ArrayList<BlockNode> tips;
   private int maxHeight;
   private TransactionPool txPool;

   /* create an empty block chain with just a genesis block.
    * Assume genesis block is a valid block
    */
   public BlockChain(Block genesisBlock) {
      blockMap = new HashMap<>();
      tips = new ArrayList<>();
      txPool = new TransactionPool();
      maxHeight = 1;

      genesisBlock.finalize();
      ByteArrayWrapper hash = new ByteArrayWrapper(genesisBlock.getHash());
      UTXOPool genesisPool = new UTXOPool();
      Transaction coinbase = genesisBlock.getCoinbase();
      genesisPool.addUTXO(new UTXO(coinbase.getHash(), 0), coinbase.getOutput(0));
      BlockNode genesisNode = new BlockNode(genesisBlock, null, genesisPool);
      blockMap.put(hash, genesisNode);
      tips.add(genesisNode);
   }

   /* Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      BlockNode result = null;
      for (BlockNode tip : tips) {
         if (result == null || tip.height > result.height ||
             (tip.height == result.height && tip.timestamp < result.timestamp)) {
            result = tip;
         }
      }
      return result != null ? result.b : null;
   }
   
   /* Get the UTXOPool for mining a new block on top of 
    * max height block
    */
   public UTXOPool getMaxHeightUTXOPool() {
      BlockNode maxNode = null;
      for (BlockNode tip : tips) {
         if (maxNode == null || tip.height > maxNode.height ||
             (tip.height == maxNode.height && tip.timestamp < maxNode.timestamp)) {
            maxNode = tip;
         }
      }
      return maxNode != null ? maxNode.getUTXOPoolCopy() : null;
   }
   
   /* Get the transaction pool to mine a new block
    */
   public TransactionPool getTransactionPool() {
      return txPool;
   }

   /* Add a block to block chain if it is valid.
    * For validity, all transactions should be valid
    * and block should be at height > (maxHeight - CUT_OFF_AGE).
    * For example, you can try creating a new block over genesis block 
    * (block height 2) if blockChain height is <= CUT_OFF_AGE + 1. 
    * As soon as height > CUT_OFF_AGE + 1, you cannot create a new block at height 2.
    * Return true of block is successfully added
    */
   public boolean addBlock(Block b) {
      if (b.getPrevBlockHash() == null) return false;
      ByteArrayWrapper prevHash = new ByteArrayWrapper(b.getPrevBlockHash());
      BlockNode parent = blockMap.get(prevHash);
      if (parent == null) return false;
      int newHeight = parent.height + 1;
      if (newHeight <= maxHeight - CUT_OFF_AGE) return false;

      // validate transactions — create a fresh handler per tx so dependent txs within
      // the same block (tx2 spends tx1's output) are validated against the updated pool
      UTXOPool poolCopy = new UTXOPool(parent.uPool);
      for (Transaction tx : b.getTransactions()) {
         if (!new TxHandler(poolCopy).isValidTx(tx)) return false;
         for (Transaction.Input in : tx.getInputs()) {
            poolCopy.removeUTXO(new UTXO(in.prevTxHash, in.outputIndex));
         }
         for (int i = 0; i < tx.getOutputs().size(); i++) {
            poolCopy.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));
         }
      }

      // add block — include this block's coinbase so the next block can spend it
      b.finalize();
      ByteArrayWrapper bHash = new ByteArrayWrapper(b.getHash());
      UTXOPool newPool = new UTXOPool(poolCopy);
      Transaction coinbaseTx = b.getCoinbase();
      newPool.addUTXO(new UTXO(coinbaseTx.getHash(), 0), coinbaseTx.getOutput(0));
      BlockNode newNode = new BlockNode(b, parent, newPool);
      blockMap.put(bHash, newNode);

      // remove txs from global pool
      for (Transaction tx : b.getTransactions()) {
         txPool.removeTransaction(tx.getHash());
      }

      // update tips
      if (parent.children.size() == 1) { // was a tip
         tips.remove(parent);
      }
      tips.add(newNode);

      // update maxHeight
      if (newHeight > maxHeight) {
         maxHeight = newHeight;
         pruneOldBlocks();
      }

      return true;
   }

   private void pruneOldBlocks() {
      ArrayList<ByteArrayWrapper> toRemove = new ArrayList<>();
      for (ByteArrayWrapper hash : blockMap.keySet()) {
         BlockNode node = blockMap.get(hash);
         if (node.height < maxHeight - CUT_OFF_AGE) {
            toRemove.add(hash);
            tips.remove(node);
         }
      }
      for (ByteArrayWrapper hash : toRemove) {
         blockMap.remove(hash);
      }
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      txPool.addTransaction(tx);
   }
}
