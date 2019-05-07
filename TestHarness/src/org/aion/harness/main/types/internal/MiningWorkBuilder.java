package org.aion.harness.main.types.internal;

import java.math.BigInteger;
import org.aion.harness.main.tools.JsonStringParser;
import org.aion.harness.main.types.StratumWork;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public class MiningWorkBuilder {
    private long height = -1;
    private byte[] previousBlockHash;
    private BigInteger target;
    private byte[] headerHash;
    private BigInteger blockBaseReward;
    private BigInteger blockTxFee;

    public MiningWorkBuilder() {}

    public StratumWork build() {
        return new StratumWork(
                height, previousBlockHash, target, headerHash, blockBaseReward, blockTxFee);
    }

    public StratumWork buildFromJsonString(String jsonString) throws DecoderException {
        JsonStringParser jsonParser = new JsonStringParser(jsonString);

        String height = jsonParser.attributeToString("height");
        String previousblockhash = jsonParser.attributeToString("previousblockhash");
        String target = jsonParser.attributeToString("target");
        String headerHash = jsonParser.attributeToString("headerHash");
        String blockBaseReward = jsonParser.attributeToString("blockBaseReward");
        String blockTxFee = jsonParser.attributeToString("blockTxFee");

        return new MiningWorkBuilder()
                .height((height == null) ? -1 : Long.parseLong(height, 10))
                .previousblockhash(
                        (previousblockhash == null) ? null : Hex.decodeHex(previousblockhash))
                .target((target == null) ? null : new BigInteger(target, 16))
                .headerHash((headerHash == null) ? null : Hex.decodeHex(headerHash))
                .blockBaseReward(
                        (blockBaseReward == null) ? null : new BigInteger(blockBaseReward, 16))
                .blockTxFee((blockTxFee == null) ? null : new BigInteger(blockTxFee, 16))
                .build();
    }

    private MiningWorkBuilder height(long l) {
        height = l;
        return this;
    }

    private MiningWorkBuilder previousblockhash(byte[] _previousblockhash) {
        previousBlockHash = _previousblockhash;
        return this;
    }

    private MiningWorkBuilder target(BigInteger _target) {
        target = _target;
        return this;
    }

    private MiningWorkBuilder headerHash(byte[] _headerHash) {
        headerHash = _headerHash;
        return this;
    }

    private MiningWorkBuilder blockBaseReward(BigInteger _blockBaseReward) {
        blockBaseReward = _blockBaseReward;
        return this;
    }

    private MiningWorkBuilder blockTxFee(BigInteger _blockTxFee) {
        blockTxFee = _blockTxFee;
        return this;
    }
}
