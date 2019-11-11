package org.aion.harness.main.types;

import java.math.BigInteger;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;

public final class Block {
    public final BigInteger number;
    public final byte[] hash;
    public final byte[] parentHash;
    public final byte[] bloomFilter;
    public final byte[] transactionsRoot;
    public final byte[] stateRoot;
    public final byte[] receiptTrieRoot;
    public final BigInteger difficulty;
    public final BigInteger totalDifficulty;
    public final long timestamp;
    public final byte[] coinbase;
    public final long blockEnergyUsed;
    public final long blockEnergyLimit;
    public final byte[] extraData;
    public final boolean mainChain;
    public final long blockSizeInBytes;

    // Mining Blocks only
    public final byte[] nonce;
    public final byte[] solution;

    // Staking Blocks only
    public final byte[] seed;
    public final byte[] signature;
    public final byte[] publicKey;

    public Block(
            BigInteger number,
            byte[] hash,
            byte[] parentHash,
            byte[] bloomFilter,
            byte[] transactionsRoot,
            byte[] stateRoot,
            byte[] receiptTrieRoot,
            BigInteger difficulty,
            BigInteger totalDifficulty,
            long timestamp,
            byte[] coinbase,
            long blockEnergyUsed,
            long blockEnergyLimit,
            byte[] extraData,
            boolean mainChain,
            long blockSizeInBytes,
            byte[] nonce,
            byte[] solution,
            byte[] seed,
            byte[] signature,
            byte[] publicKey) {

        this.number = number;
        this.hash = Arrays.copyOf(hash, hash.length);
        this.parentHash = Arrays.copyOf(parentHash, parentHash.length);
        this.bloomFilter = Arrays.copyOf(bloomFilter, bloomFilter.length);
        this.transactionsRoot = Arrays.copyOf(transactionsRoot, transactionsRoot.length);
        this.stateRoot = Arrays.copyOf(stateRoot, stateRoot.length);
        this.receiptTrieRoot = Arrays.copyOf(receiptTrieRoot, receiptTrieRoot.length);
        this.difficulty = difficulty;
        this.totalDifficulty = totalDifficulty;
        this.timestamp = timestamp;
        this.coinbase = Arrays.copyOf(coinbase, coinbase.length);
        this.blockEnergyUsed = blockEnergyUsed;
        this.blockEnergyLimit = blockEnergyLimit;
        this.extraData = Arrays.copyOf(extraData, extraData.length);
        this.mainChain = mainChain;
        this.blockSizeInBytes = blockSizeInBytes;
        this.nonce = (nonce == null) ? null : Arrays.copyOf(nonce, nonce.length);
        this.solution = (solution == null) ? null : Arrays.copyOf(solution, solution.length);
        this.seed = (seed == null) ? null : Arrays.copyOf(seed, seed.length);
        this.signature = (signature == null) ? null : Arrays.copyOf(signature, signature.length);
        this.publicKey = (publicKey == null) ? null : Arrays.copyOf(publicKey, publicKey.length);
    }

    @Override
    public String toString() {
        return "Block { number = " + this.number
            + ", hash = 0x" + Hex.encodeHexString(this.hash)
            + ", parent hash = 0x" + Hex.encodeHexString(this.parentHash)
            + ", bloom filter = 0x" + Hex.encodeHexString(this.bloomFilter) + " }"
            + ", transactions root = 0x" + Hex.encodeHexString(this.transactionsRoot)
            + ", state root = 0x" + Hex.encodeHexString(this.stateRoot)
            + ", receipts root = 0x" + Hex.encodeHexString(this.receiptTrieRoot)
            + ", difficulty = " + this.difficulty
            + ", total difficulty = " + this.totalDifficulty
            + ", timestamp = " + this.timestamp
            + ", coinbase = 0x" + Hex.encodeHexString(this.coinbase)
            + ", block energy used = " + this.blockEnergyUsed
            + ", block energy limit = " + this.blockEnergyLimit
            + ", extraData = 0x" + Hex.encodeHexString(this.extraData)
            + ", mainChain = " + this.mainChain
            + ", block size (in bytes) = " + this.blockSizeInBytes
            + ", nonce = 0x" + Hex.encodeHexString(this.nonce)
            + ", solution = 0x" + Hex.encodeHexString(this.solution)
            + ", seed = 0x" + Hex.encodeHexString(this.seed)
            + ", signature = 0x" + Hex.encodeHexString(this.signature)
            + ", publicKey = 0x" + Hex.encodeHexString(this.publicKey);
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
