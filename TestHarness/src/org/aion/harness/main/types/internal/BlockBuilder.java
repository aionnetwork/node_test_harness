package org.aion.harness.main.types.internal;

import java.math.BigInteger;
import org.aion.harness.main.tools.JsonStringParser;
import org.aion.harness.main.types.Block;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public final class BlockBuilder {
    private long difficulty = -1;
    private long blockSizeInBytes = -1;
    private long blockEnergyLimit = -1;
    private long blockEnergyUsed = -1;
    private byte[] hash = null;
    private byte[] parentHash = null;
    private byte[] bloomFilter = null;
    private byte[] receiptTrieRoot = null;
    private byte[] stateRoot = null;
    private byte[] nonce = null;
    private BigInteger number = null;
    private BigInteger totalDifficulty = null;

    public BlockBuilder difficulty(long difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public BlockBuilder blockSize(long size) {
        this.blockSizeInBytes = size;
        return this;
    }

    public BlockBuilder energyLimit(long limit) {
        this.blockEnergyLimit = limit;
        return this;
    }

    public BlockBuilder energyUsed(long used) {
        this.blockEnergyUsed = used;
        return this;
    }

    public BlockBuilder hash(byte[] hash) {
        this.hash = hash;
        return this;
    }

    public BlockBuilder parentHash(byte[] parentHash) {
        this.parentHash = parentHash;
        return this;
    }

    public BlockBuilder bloomFilter(byte[] bloom) {
        this.bloomFilter = bloom;
        return this;
    }

    public BlockBuilder receiptTrieRoot(byte[] receiptRoot) {
        this.receiptTrieRoot = receiptRoot;
        return this;
    }

    public BlockBuilder stateRoot(byte[] stateRoot) {
        this.stateRoot = stateRoot;
        return this;
    }

    public BlockBuilder nonce(byte[] nonce) {
        this.nonce = nonce;
        return this;
    }

    public BlockBuilder number(BigInteger number) {
        this.number = number;
        return this;
    }

    public BlockBuilder totalDifficulty(BigInteger total) {
        this.totalDifficulty = total;
        return this;
    }

    public Block build() {
        if (this.difficulty < 0) {
            throw new IllegalStateException("Cannot build block with no difficulty set.");
        }
        if (this.blockSizeInBytes < 0) {
            throw new IllegalStateException("Cannot build block with no block size set.");
        }
        if (this.blockEnergyLimit < 0) {
            throw new IllegalStateException("Cannot build block with no block energy limit set.");
        }
        if (this.blockEnergyUsed < 0) {
            throw new IllegalStateException("Cannot build block with no block energy used set.");
        }
        if (this.hash == null) {
            throw new IllegalStateException("Cannot build block with no hash set.");
        }
        if (this.parentHash == null) {
            throw new IllegalStateException("Cannot build block with no parent hash set.");
        }
        if (this.bloomFilter == null) {
            throw new IllegalStateException("Cannot build block with no bloom filter set.");
        }
        if (this.receiptTrieRoot == null) {
            throw new IllegalStateException("Cannot build block with no receipts trie root set.");
        }
        if (this.stateRoot == null) {
            throw new IllegalStateException("Cannot build block with no state trie set.");
        }
        if (this.number == null) {
            throw new IllegalStateException("Cannot build block with no block number set.");
        }
        if (this.totalDifficulty == null) {
            throw new IllegalStateException("Cannot build block with no total difficulty set.");
        }

        return new Block(
            this.difficulty,
            this.blockSizeInBytes,
            this.blockEnergyLimit,
            this.blockEnergyUsed,
            this.hash,
            this.parentHash,
            this.bloomFilter,
            this.receiptTrieRoot,
            this.stateRoot,
            this.nonce,
            this.number,
            this.totalDifficulty);
    }

    public Block buildFromJsonString(String jsonString) throws DecoderException  {
        JsonStringParser jsonParser = new JsonStringParser(jsonString);

        String difficulty = jsonParser.attributeToString("difficulty");
        String size = jsonParser.attributeToString("size");
        String energyLimit = jsonParser.attributeToString("gasLimit");
        String energyUsed = jsonParser.attributeToString("gasUsed");
        String hash = jsonParser.attributeToString("hash");
        String parentHash = jsonParser.attributeToString("parentHash");
        String bloom = jsonParser.attributeToString("logsBloom");
        String receiptRoot = jsonParser.attributeToString("transactionsRoot");
        String stateRoot = jsonParser.attributeToString("stateRoot");
        String nonce = jsonParser.attributeToString("nonce");
        String number = jsonParser.attributeToString("number");
        String totalDifficulty = jsonParser.attributeToString("totalDifficulty");

        return new BlockBuilder()
            .difficulty((difficulty == null) ? -1 : Long.parseLong(difficulty, 16))
            .blockSize((size == null) ? -1 : Long.parseLong(size, 16))
            .energyLimit((energyLimit == null) ? -1 : Long.parseLong(energyLimit, 16))
            .energyUsed((energyUsed == null) ? -1 : Long.parseLong(energyUsed, 16))
            .hash((hash == null) ? null : Hex.decodeHex(hash))
            .parentHash((parentHash == null) ? null : Hex.decodeHex(parentHash))
            .bloomFilter((bloom == null) ? null : Hex.decodeHex(bloom))
            .receiptTrieRoot((receiptRoot == null) ? null : Hex.decodeHex(receiptRoot))
            .stateRoot((stateRoot == null) ? null : Hex.decodeHex(stateRoot))
            .nonce((nonce == null) ? null : Hex.decodeHex(nonce))
            .number((number == null) ? null : new BigInteger(number, 16))
            .totalDifficulty((totalDifficulty == null) ? null : new BigInteger(totalDifficulty, 16))
            .build();
    }

    /**
     * Restores this builder to its initial empty state.
     */
    public void clear() {
        this.difficulty = -1;
        this.blockSizeInBytes = -1;
        this.blockEnergyLimit = -1;
        this.blockEnergyUsed = -1;
        this.hash = null;
        this.parentHash = null;
        this.bloomFilter = null;
        this.receiptTrieRoot = null;
        this.stateRoot = null;
        this.nonce = null;
        this.number = null;
        this.totalDifficulty = null;
    }

}
