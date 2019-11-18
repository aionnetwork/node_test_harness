package org.aion.harness.tests.contracts.avm;

import avm.Blockchain;
import java.math.BigInteger;
import org.aion.avm.userlib.abi.ABIDecoder;

public class LogTarget {
    public static byte[] data;
    public static byte[] topic1;
    public static byte[] topic2;
    public static byte[] topic3;
    public static byte[] topic4;

    static {
        data = new byte[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        topic1 = new byte[]{ 9, 5, 5, 2, 3, 8, 1 };
        topic2 = new byte[]{ 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6 };
        topic3 = new byte[]{ 0xf };
        topic4 = new byte[0];
        
        // We also want to write a log entry and check for it on the other side (we will use all the topics, just to make more filtering an option).
        writeDataLogWithFourTopics();
    }

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String method = decoder.decodeMethodName();

        if (method.equals("writeNoLogs")) {
            writeNoLogs();
        } else if (method.equals("writeDataOnlyLog")) {
            writeDataOnlyLog();
        } else if (method.equals("writeDataLogWithOneTopic")) {
            writeDataLogWithOneTopic();
        } else if (method.equals("writeDataLogWithTwoTopics")) {
            writeDataLogWithTwoTopics();
        } else if (method.equals("writeDataLogWithThreeTopics")) {
            writeDataLogWithThreeTopics();
        } else if (method.equals("writeDataLogWithFourTopics")) {
            writeDataLogWithFourTopics();
        } else if (method.equals("writeAllLogs")) {
            writeAllLogs();
        } else if (method.equals("writeLogsFromInternalCallAlso")) {
            writeLogsFromInternalCallAlso(decoder);
        } else {
            Blockchain.revert();
        }

        return null;
    }

    public static void writeNoLogs() {}

    public static void writeDataOnlyLog() {
        Blockchain.log(data);
    }

    public static void writeDataLogWithOneTopic() {
        Blockchain.log(topic1, data);
    }

    public static void writeDataLogWithTwoTopics() {
        Blockchain.log(topic1, topic2, data);
    }

    public static void writeDataLogWithThreeTopics() {
        Blockchain.log(topic1, topic2, topic3, data);
    }

    public static void writeDataLogWithFourTopics() {
        Blockchain.log(topic1, topic2, topic3, topic4, data);
    }

    public static void writeAllLogs() {
        writeDataOnlyLog();
        writeDataLogWithOneTopic();
        writeDataLogWithTwoTopics();
        writeDataLogWithThreeTopics();
        writeDataLogWithFourTopics();
    }

    public static void writeLogsFromInternalCallAlso(ABIDecoder decoder) {
        writeAllLogs();
        Blockchain.require(Blockchain.call(decoder.decodeOneAddress(), BigInteger.ZERO, decoder.decodeOneByteArray(), Blockchain.getRemainingEnergy()).isSuccess());
    }
}
