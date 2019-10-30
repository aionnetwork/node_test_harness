package org.aion.harness.main.types;

import java.math.BigInteger;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;

public final class Block {
    private final long difficulty;
    private final long blockSizeInBytes;
    private final long blockEnergyLimit;
    private final long blockEnergyUsed;
    private final byte[] hash;
    private final byte[] parentHash;
    private final byte[] bloomFilter;
    private final byte[] receiptTrieRoot;
    private final byte[] stateRoot;
    private final BigInteger number;
    private final BigInteger totalDifficulty;

    public Block(long difficulty, long blockSizeInBytes, long blockEnergyLimit, long blockEnergyUsed,
        byte[] hash, byte[] parentHash, byte[] bloomFilter, byte[] receiptTrieRoot, byte[] stateRoot,
        byte[] nonce, BigInteger number, BigInteger totalDifficulty) {

        this.difficulty = difficulty;
        this.blockSizeInBytes = blockSizeInBytes;
        this.blockEnergyLimit = blockEnergyLimit;
        this.blockEnergyUsed = blockEnergyUsed;
        this.hash = Arrays.copyOf(hash, hash.length);
        this.parentHash = Arrays.copyOf(parentHash, parentHash.length);
        this.bloomFilter = Arrays.copyOf(bloomFilter, bloomFilter.length);
        this.receiptTrieRoot = Arrays.copyOf(receiptTrieRoot, receiptTrieRoot.length);
        this.stateRoot = Arrays.copyOf(stateRoot, stateRoot.length);
        this.number = number;
        this.totalDifficulty = totalDifficulty;
    }

    /**
     * Returns the difficulty for this block.
     *
     * @return the block difficulty.
     */
    public long blockDifficulty() {
        return this.difficulty;
    }

    /**
     * Returns the size of this block in bytes.
     *
     * @return the size of the block.
     */
    public long blockSizeInBytes() {
        return this.blockSizeInBytes;
    }

    /**
     * Returns the total amount of energy that is allowed in this block.
     *
     * @return the block's maximum energy limit.
     */
    public long getBlockEnergyLimit() {
        return this.blockEnergyLimit;
    }

    /**
     * Returns the amount of energy used by all of the transactions in this block.
     *
     * @return the block's total energy used.
     */
    public long getBlockEnergyUsed() {
        return this.blockEnergyUsed;
    }

    /**
     * Returns the hash of this block.
     *
     * @return the block's hash.
     */
    public byte[] getBlockHash() {
        return Arrays.copyOf(this.hash, this.hash.length);
    }

    /**
     * Returns the hash of the block that is the parent of this block.
     *
     * If this block is block number N then its parent is block number N-1.
     *
     * @return the parent block's hash.
     */
    public byte[] getParentBlockHash() {
        return Arrays.copyOf(this.parentHash, this.parentHash.length);
    }

    /**
     * Returns the bloom filter for all of the logs in this block.
     *
     * @return the bloom filter for the logs in this block.
     */
    public byte[] getBloomFilter() {
        return Arrays.copyOf(this.bloomFilter, this.bloomFilter.length);
    }

    /**
     * Returns the root of the final receipts trie for the block.
     *
     * @return the root of the receipts trie for the receipts in this block.
     */
    public byte[] getTransactionReceiptsTrieRoot() {
        return Arrays.copyOf(this.receiptTrieRoot, this.receiptTrieRoot.length);
    }

    /**
     * Returns the root of the final state trie for the block.
     *
     * @return the root of the state trie for the block.
     */
    public byte[] getStateRoot() {
        return Arrays.copyOf(this.stateRoot, this.stateRoot.length);
    }

    /**
     * Returns the number of this block.
     *
     * @return the block's number.
     */
    public BigInteger getBlockNumber() {
        return this.number;
    }

    /**
     * Returns the sum of all the block difficulties up to and including this block.
     *
     * @return the total difficulty of the chain at this block.
     */
    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    @Override
    public String toString() {
        return "Block { number = " + this.number
            + ", difficulty = " + this.difficulty
            + ", total difficulty = " + this.totalDifficulty
            + ", block energy limit = " + this.blockEnergyLimit
            + ", block energy used = " + this.blockEnergyUsed
            + ", block size (in bytes) = " + this.blockSizeInBytes
            + ", hash = 0x" + Hex.encodeHexString(this.hash)
            + ", parent hash = 0x" + Hex.encodeHexString(this.parentHash)
            + ", state root = 0x" + Hex.encodeHexString(this.stateRoot)
            + ", receipts root = 0x" + Hex.encodeHexString(this.receiptTrieRoot)
            + ", bloom filter = 0x" + Hex.encodeHexString(this.bloomFilter) + " }";
    }

    /**
     * Returns {@code true} if, and only if, other is a block and its hash is equal to this block's
     * hash and both other and this block have the same total difficulty.
     *
     * These two values should be sufficient for equality and should imply that all of the other
     * fields are equal as well. If not, then there is likely an error in the kernel.
     *
     * @param other The other object whose equality is to be tested.
     * @return true if other is a block with the same hash and total difficulty.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Block)) {
            return false;
        }

        Block otherBlock = (Block) other;

        if (!Arrays.equals(this.hash, otherBlock.hash)) {
            return false;
        }
        return this.totalDifficulty.equals(otherBlock.totalDifficulty);
    }
    
    @Override
    public int hashCode() {
        int hash = 37;
        hash += Arrays.hashCode(this.hash);
        hash += this.totalDifficulty.intValue();
        return hash;
    }

}
