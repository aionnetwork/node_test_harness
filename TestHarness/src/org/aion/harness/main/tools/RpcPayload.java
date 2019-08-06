package org.aion.harness.main.tools;

/**
 * A class that holds the payload for an RPC call. The payload for an RPC call is simply the data
 * to that call (following the --data option).
 *
 * The preferred way of constructing an instance of this class is to the use the
 * {@link RpcPayloadBuilder}.
 *
 * An rpc payload is immutable.
 */
public final class RpcPayload {
    public final String payload;

    public RpcPayload(String payload) {
        this.payload = payload;
    }

    public RpcPayload(RpcMethod method, String params, String defaultBlock) {
        if (method == null) {
            throw new NullPointerException("Cannot construct rpc payload with null method.");
        }
        if (params == null) {
            throw new NullPointerException("Cannot construct rpc payload with null params.");
        }
        if (defaultBlock == null) {
            throw new NullPointerException("Cannot construct rpc payload with null default block.");
        }

        String parameters;
        if ((!params.isEmpty()) && (!defaultBlock.isEmpty())) {
            parameters = "\"" + params + "\",\"" + defaultBlock + "\"";
        } else if ((!params.isEmpty()) && (defaultBlock.isEmpty())) {
            parameters = params;
        } else {
            parameters = "";
        }

        this.payload = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method.getMethod() + "\",\"params\":[" + parameters + "],\"id\":1}";
    }
}
