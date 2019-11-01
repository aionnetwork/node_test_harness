package org.aion.harness.main.types;

public class MinedBlockSolution {
    private final byte[] headerHash;
    private final byte[] nonce;
    private final byte[] solution;

    public MinedBlockSolution(byte[] headerHash, byte[] nonce, byte[] solution) {
        this.headerHash = headerHash.clone();
        this.nonce = nonce.clone();
        this.solution = solution.clone();
    }

    public byte[] getHeaderHash() {
        return headerHash.clone();
    }

    public byte[] getNonce() {
        return nonce.clone();
    }

    public byte[] getSolution() {
        return solution.clone();
    }
}
