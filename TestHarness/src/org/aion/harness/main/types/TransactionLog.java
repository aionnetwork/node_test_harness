package org.aion.harness.main.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.harness.kernel.Address;
import org.apache.commons.codec.binary.Hex;

/**
 * A log that was fired off during a transaction.
 *
 * A log has an address, which is the address of the contract that fired the log, as well as data.
 * A log may have zero or more topics associated with it.
 *
 * This class also records the block number that the transaction is in, the index of the transaction
 * that fired the log in the block, as well as the index of the log in the list of logs in the
 * block.
 *
 * A transaction log is immutable.
 */
public final class TransactionLog {
    public final Address address;
    private final byte[] data;
    private final List<byte[]> topics;
    public final BigInteger blockNumber;
    private final byte[] blockHash;
    public final int transactionIndex;
    public final int logIndex;

    public TransactionLog(Address address, byte[] data, List<byte[]> topics, BigInteger blockNumber, byte[] blockHash, int transactionIndex, int logIndex) {
        this.address = address;
        this.data = Arrays.copyOf(data, data.length);
        this.topics = copyOfBytesList(topics);
        this.blockNumber = blockNumber;
        // Note that "blockHash" doesn't appear in the logs when fetched from the receipt so it can be null.
        this.blockHash = (null != blockHash)
                ? Arrays.copyOf(blockHash, blockHash.length)
                : null;
        this.transactionIndex = transactionIndex;
        this.logIndex = logIndex;
    }

    /**
     * Returns a copy of the log data.
     *
     * @return the log data.
     */
    public byte[] copyOfData() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    /**
     * Returns a copy of the log topics.
     *
     * @return the log topics.
     */
    public List<byte[]> copyOfTopics() {
        return copyOfBytesList(this.topics);
    }

    /**
     * Returns a copy of the block hash.
     *
     * @return the block hash.
     */
    public byte[] copyOfBlockHash() {
        return (null != blockHash)
                ? Arrays.copyOf(this.blockHash, this.blockHash.length)
                : null;
    }

    @Override
    public String toString() {
        return "TransactionLog { address = " + this.address + ", topics = [" + topicsToString()
            + "], block number = " + this.blockNumber + ", transaction index = " + this.transactionIndex
            + ", log index = " + this.logIndex + " }";
    }

    /**
     * Returns {@code true} only if other is a {@link TransactionLog} and the two logs have the same
     * address, data and list of topics (where order matters!).
     *
     * Returns {@code false} otherwise.
     *
     * Note that block number as well as transaction & log indices do not determine whether two logs
     * are equal, since these are 'accidental' pieces of data that are a consequence of when a log
     * was fired off, rather than what actually constitutes that log.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TransactionLog)) {
            return false;
        } else if (other == this) {
            return true;
        }

        TransactionLog otherLog = (TransactionLog) other;
        if (!this.address.equals(otherLog.address)) {
            return false;
        }
        if (!Arrays.equals(this.data, otherLog.data)) {
            return false;
        }
        if (!Arrays.equals(this.blockHash, otherLog.blockHash)) {
            return false;
        }

        return bytesListsAreEqual(this.topics, otherLog.topics);
    }

    @Override
    public int hashCode() {
        int hash = this.address.hashCode() + Arrays.hashCode(this.data);
        for (byte[] topic : this.topics) {
            hash += Arrays.hashCode(topic);
        }
        return hash;
    }

    private String topicsToString() {
        StringBuilder builder = new StringBuilder();

        int index = 0;
        for (byte[] topic : this.topics) {
            builder.append("0x").append(Hex.encodeHexString(topic));

            if (index < this.topics.size() - 1) {
                builder.append(", ");
            }

            index++;
        }
        return builder.toString();
    }

    private static List<byte[]> copyOfBytesList(List<byte[]> bytesList) {
        List<byte[]> copy = new ArrayList<>();
        for (byte[] bytes : bytesList) {
            copy.add(Arrays.copyOf(bytes, bytes.length));
        }
        return copy;
    }

    private static boolean bytesListsAreEqual(List<byte[]> bytesList1, List<byte[]> bytesList2) {
        if (bytesList1.size() != bytesList2.size()) {
            return false;
        } else {
            int length = bytesList1.size();

            for (int i = 0; i < length; i++) {
                if (!Arrays.equals(bytesList1.get(i), bytesList2.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
