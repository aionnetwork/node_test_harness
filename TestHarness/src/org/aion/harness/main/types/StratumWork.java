package org.aion.harness.main.types;

import java.math.BigInteger;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;

/**
 * A class that holds the StratumWork of a mining node.
 *
 * <p>This class is immutable.
 */
public final class StratumWork {
    public final long height;
    public final byte[] previousblockhash;
    public final BigInteger target;
    public final byte[] headerHash;
    public final BigInteger blockBaseReward;
    public final BigInteger blockTxFee;

    public StratumWork(
            long height,
            byte[] previousblockhash,
            BigInteger target,
            byte[] headerHash,
            BigInteger blockBaseReward,
            BigInteger blockTxFee) {
        this.height = height;
        this.previousblockhash = previousblockhash;
        this.target = target;
        this.headerHash = headerHash;
        this.blockBaseReward = blockBaseReward;
        this.blockTxFee = blockTxFee;
    }

    @Override
    public String toString() {
        return "StratumWork { mining block height = "
                + height
                + ", previousblockhash = "
                + Hex.encodeHexString(previousblockhash)
                + ", target = "
                + target
                + ", headerHash = "
                + Hex.encodeHexString(headerHash)
                + ", blockBaseReward = "
                + blockBaseReward
                + ", blockTxFee = "
                + blockTxFee
                + " }";
    }

    /**
     * Returns {@code true} if, and only if, other is a sync status object and other this sync
     * status and other report the same starting, current and highest block numbers as well as the
     * same {@code isSyncing()} and {@code isWaitingToConnect()} values.
     *
     * @param other The other object whose equality is to be tested.
     * @return true if other is a StratumWork object with the same mining status as this one.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StratumWork)) {
            return false;
        }

        StratumWork otherCast = (StratumWork) other;
        return (this.hashCode() == otherCast.hashCode());
    }

    @Override
    public int hashCode() {
        int hash = 137;
        hash +=
                Arrays.hashCode(this.previousblockhash)
                        + this.target.hashCode()
                        + Arrays.hashCode(headerHash)
                        + blockBaseReward.hashCode()
                        + blockTxFee.hashCode();
        hash += height << 16;
        return hash;
    }
}
