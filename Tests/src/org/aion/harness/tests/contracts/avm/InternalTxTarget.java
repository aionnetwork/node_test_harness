package org.aion.harness.tests.contracts.avm;

import java.math.BigInteger;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

public class InternalTxTarget {

    private static int depth = 0;

    public static byte[] main() {
        Blockchain.println("Internal depth of " + depth);
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("callSelfToGetSix")) {
                return ABIEncoder.encodeOneInteger(callSelfToGetSix(decoder.decodeOneInteger()));
            } else {
                return new byte[0];
            }
        }
    }

    public static int callSelfToGetSix(int numberOfTimesToCallSelf) {
        if (numberOfTimesToCallSelf-- <= 0) {
            return 6;
        } else {
            return reentrantCall("callSelfToGetSix", numberOfTimesToCallSelf);
        }
    }

    private static int reentrantCall(String methodName, int numberOfTimes) {
        depth++;
        BigInteger value = BigInteger.ZERO;

        byte[] methodNameBytes = ABIEncoder.encodeOneString(methodName);
        byte[] parameterBytes = ABIEncoder.encodeOneInteger(numberOfTimes);
        byte[] data = new byte[methodNameBytes.length + parameterBytes.length];
        System.arraycopy(methodNameBytes, 0, data, 0, methodNameBytes.length);
        System.arraycopy(parameterBytes, 0, data, methodNameBytes.length, parameterBytes.length);

        Result txResult =  Blockchain.call(Blockchain.getAddress(), value, data, 1_000_000L);
        if (!txResult.isSuccess()) {
            Blockchain.println("Failed at depth " + (depth + 1));
        }
        Blockchain.require(txResult.isSuccess());
        byte[] result = txResult.getReturnData();
        return new ABIDecoder(result).decodeOneInteger();
    }
}
