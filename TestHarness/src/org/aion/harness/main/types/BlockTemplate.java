package org.aion.harness.main.types;

import java.math.BigInteger;

public class BlockTemplate {
    public final BigInteger difficultyTarget;
    private final byte[] headerHash;

    public BlockTemplate(BigInteger difficultyTarget, byte[] headerHash) {
        this.difficultyTarget = difficultyTarget;
        this.headerHash = headerHash.clone();
    }

    public byte[] getHeaderHash() {
        return headerHash.clone();
    }

    public BlockTemplate copy() {
        return new BlockTemplate(difficultyTarget, headerHash);
    }
}
