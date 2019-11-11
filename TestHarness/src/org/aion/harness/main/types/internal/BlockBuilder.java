package org.aion.harness.main.types.internal;

import java.math.BigInteger;
import org.aion.harness.main.tools.JsonStringParser;
import org.aion.harness.main.types.Block;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public final class BlockBuilder {
    private BigInteger number = null;
    private byte[] hash = null;
    private byte[] parentHash = null;
    private byte[] bloomFilter = null;
    private byte[] transactionsRoot = null;
    private byte[] stateRoot = null;
    private byte[] receiptTrieRoot = null;
    private BigInteger difficulty = null;
    private BigInteger totalDifficulty = null;
    private long timestamp = -1;
    private byte[] coinbase = null;
    private long blockEnergyUsed = -1;
    private long blockEnergyLimit = -1;
    private byte[] extraData = null;
    private boolean mainChain = false;
    private long blockSizeInBytes = -1;

    // Mining Blocks only
    private byte[] nonce = null;
    private byte[] solution = null;

    // Staking Blocks only
    private byte[] seed = null;
    private byte[] signature = null;
    private byte[] publicKey = null;

    public BlockBuilder number(BigInteger number) {
        this.number = number;
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

    public BlockBuilder transactionsRoot(byte[] transactionsRoot) {
        this.transactionsRoot = transactionsRoot;
        return this;
    }

    public BlockBuilder stateRoot(byte[] stateRoot) {
        this.stateRoot = stateRoot;
        return this;
    }

    public BlockBuilder receiptTrieRoot(byte[] receiptRoot) {
        this.receiptTrieRoot = receiptRoot;
        return this;
    }

    public BlockBuilder difficulty(BigInteger difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public BlockBuilder totalDifficulty(BigInteger total) {
        this.totalDifficulty = total;
        return this;
    }

    public BlockBuilder timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public BlockBuilder coinbase(byte[] coinbase) {
        this.coinbase = coinbase;
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

    public BlockBuilder extraData(byte[] extraData) {
        this.extraData = extraData;
        return this;
    }

    public BlockBuilder blockSize(long size) {
        this.blockSizeInBytes = size;
        return this;
    }

    public BlockBuilder mainChain(boolean mainChain) {
        this.mainChain = mainChain;
        return this;
    }

    public BlockBuilder nonce(byte[] nonce) {
        this.nonce = nonce;
        return this;
    }

    public BlockBuilder solution(byte[] solution) {
        this.solution = solution;
        return this;
    }

    public BlockBuilder seed(byte[] seed) {
        this.seed = seed;
        return this;
    }

    public BlockBuilder signature(byte[] signature) {
        this.signature = signature;
        return this;
    }

    public BlockBuilder publicKey(byte[] publicKey) {
        this.publicKey = publicKey;
        return this;
    }


    public Block build() {
        if (this.number == null) {
            throw new IllegalStateException("Cannot build block with no block number set.");
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
        if (this.stateRoot == null) {
            throw new IllegalStateException("Cannot build block with no state trie set.");
        }
        if (this.receiptTrieRoot == null) {
            throw new IllegalStateException("Cannot build block with no receipts trie root set.");
        }
        if (this.difficulty == null) {
            throw new IllegalStateException("Cannot build block with no difficulty set.");
        }
        if (this.totalDifficulty == null) {
            throw new IllegalStateException("Cannot build block with no total difficulty set.");
        }
        if (this.blockEnergyUsed < 0) {
            throw new IllegalStateException("Cannot build block with no block energy used set.");
        }
        if (this.blockEnergyLimit < 0) {
            throw new IllegalStateException("Cannot build block with no block energy limit set.");
        }
        if (this.blockSizeInBytes < 0) {
            throw new IllegalStateException("Cannot build block with no block size set.");
        }

        return new Block(
            number,
            hash,
            parentHash,
            bloomFilter,
            transactionsRoot,
            stateRoot,
            receiptTrieRoot,
            difficulty,
            totalDifficulty,
            timestamp,
            coinbase,
            blockEnergyUsed,
            blockEnergyLimit,
            extraData,
            mainChain,
            blockSizeInBytes,
            nonce,
            solution,
            seed,
            signature,
            publicKey);
    }

    public Block buildFromJsonString(String jsonString) throws DecoderException  {
        JsonStringParser jsonParser = new JsonStringParser(jsonString);

        String number = jsonParser.attributeToString("number");
        String hash = jsonParser.attributeToString("hash");
        String parentHash = jsonParser.attributeToString("parentHash");
        String bloom = jsonParser.attributeToString("logsBloom");
        String transactionsRoot = jsonParser.attributeToString("transactionsRoot");
        String stateRoot = jsonParser.attributeToString("stateRoot");
        String receiptsRoot = jsonParser.attributeToString("receiptsRoot");
        String difficulty = jsonParser.attributeToString("difficulty");
        String totalDifficulty = jsonParser.attributeToString("totalDifficulty");
        String timestamp = jsonParser.attributeToString("timestamp");
        String miner = jsonParser.attributeToString("miner");
        String energyUsed = jsonParser.attributeToString("gasUsed");
        String energyLimit = jsonParser.attributeToString("gasLimit");
        String extraData = jsonParser.attributeToString("extraData");
        //String sealType = jsonParser.attributeToString("sealType");
        String mainChain = jsonParser.attributeToString("mainChain");
        String size = jsonParser.attributeToString("size");

        // Mining block only fields
        String nonce = jsonParser.attributeToString("nonce");
        String solution = jsonParser.attributeToString("solution");

        // Staking block only fields
        String seed = jsonParser.attributeToString("seed");
        String signature = jsonParser.attributeToString("signature");
        String publicKey = jsonParser.attributeToString("publicKey");

        return new BlockBuilder()
            .number((number == null) ? null : new BigInteger(number, 16))
            .hash((hash == null) ? null : Hex.decodeHex(hash))
            .parentHash((parentHash == null) ? null : Hex.decodeHex(parentHash))
            .bloomFilter((bloom == null) ? null : Hex.decodeHex(bloom))
            .transactionsRoot((transactionsRoot == null) ? null : Hex.decodeHex(transactionsRoot))
            .stateRoot((stateRoot == null) ? null : Hex.decodeHex(stateRoot))
            .receiptTrieRoot((receiptsRoot == null) ? null : Hex.decodeHex(receiptsRoot))
            .difficulty((difficulty == null) ? BigInteger.ZERO : new BigInteger(difficulty, 16))
            .totalDifficulty((totalDifficulty == null) ? null : new BigInteger(totalDifficulty, 16))
            .timestamp((timestamp == null) ? -1 : Long.parseLong(timestamp, 16))
            .coinbase((miner == null) ? null : Hex.decodeHex(miner))
            .energyUsed((energyUsed == null) ? -1 : Long.parseLong(energyUsed, 16))
            .energyLimit((energyLimit == null) ? -1 : Long.parseLong(energyLimit, 16))
            .extraData((extraData == null) ? null : Hex.decodeHex(extraData))
            .mainChain(mainChain != null && mainChain.equals("true"))
            .blockSize((size == null) ? -1 : Integer.parseInt(size, 16))
            .nonce((nonce == null) ? null : Hex.decodeHex(nonce))
            .solution((solution == null) ? null : Hex.decodeHex(solution))
            .seed((seed == null) ? null : Hex.decodeHex(seed))
            .signature((signature == null) ? null : Hex.decodeHex(signature))
            .publicKey((publicKey == null) ? null : Hex.decodeHex(publicKey))
            .build();
    }

    /**
     * Restores this builder to its initial empty state.
     */
    public void clear() {
        number = null;
        hash = null;
        parentHash = null;
        bloomFilter = null;
        transactionsRoot = null;
        stateRoot = null;
        receiptTrieRoot = null;
        difficulty = null;
        totalDifficulty = null;
        timestamp = -1;
        coinbase = null;
        blockEnergyUsed = -1;
        blockEnergyLimit = -1;
        extraData = null;
        mainChain = false;
        blockSizeInBytes = -1;
        nonce = null;
        solution = null;
        seed = null;
        signature = null;
        publicKey = null;
    }

}
