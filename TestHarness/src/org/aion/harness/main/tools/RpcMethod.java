package org.aion.harness.main.tools;

/**
 * An enum that serves as a mapping between an enum type and a String. Namely, the String that
 * is to be put into the payload for an RPC call.
 */
public enum RpcMethod {

    SEND_RAW_TRANSACTION("eth_sendRawTransaction"),

    SEND_TRANSACTION("eth_sendTransaction"),

    GET_TRANSACTION_RECEIPT("eth_getTransactionReceipt"),

    GET_BLOCK_BY_NUMBER("eth_getBlockByNumber"),

    GET_BALANCE("eth_getBalance"),

    IS_SYNCED("eth_syncing"),

    GET_NONCE("eth_getTransactionCount"),

    BLOCK_NUMBER("eth_blockNumber"),

    GET_TRANSACTION_BY_HASH("eth_getTransactionByHash"),

    GET_BLOCK_TEMPLATE("getblocktemplate"),

    SUBMIT_BLOCK("submitblock"),

    CALL("eth_call"),

    UNLOCK_ACCOUNT("personal_unlockAccount");

    private String method;

    private RpcMethod(String method) {
        this.method = method;
    }

    /**
     * Returns the method name corresponding to this enumeration for an RPC call.
     *
     * @return the method name.
     */
    public String getMethod() {
        return this.method;
    }

    @Override
    public String toString() {
        return "RpcMethod { " + this.method + " }";
    }

}
