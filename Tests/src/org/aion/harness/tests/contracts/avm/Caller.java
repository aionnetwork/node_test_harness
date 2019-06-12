package org.aion.harness.tests.contracts.avm;

import avm.Blockchain;

public class Caller {
    public static byte[] main() {
        return Blockchain.getCaller().toByteArray();
    }
}

