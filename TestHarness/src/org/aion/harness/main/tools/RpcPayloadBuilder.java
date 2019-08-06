package org.aion.harness.main.tools;

/**
 * A class for constructing new instances of {@link RpcPayload}.
 *
 * Subsequent calls to {@code build()} will reuse the details that were originally set.
 *
 * If a method is invoked multiple times before a call to {@code build()} then its latest
 * invocation takes precedence.
 *
 * An rpc payload builder is not thread-safe.
 */
public final class RpcPayloadBuilder {
    private RpcMethod method;
    private String params;
    private String defaultBlock;

    /**
     * The RPC method to call.
     *
     * @param method The method to call.
     * @return this builder.
     */
    public RpcPayloadBuilder method(RpcMethod method) {
        this.method = method;
        return this;
    }

    /**
     * The string to insert into the 'params' section of the RPC payload.
     *
     * @param params The parameters.
     * @return this builder.
     */
    public RpcPayloadBuilder params(String params) {
        if (this.params == null) {
            this.params = '\"' + params + '\"';
        } else {
            this.params += ", \"" + params + '\"';
        }
        return this;
    }

    /**
     * The block that the RPC call will derive its world-view from will be the latest (the most
     * current) block.
     *
     * @return this builder.
     */
    public RpcPayloadBuilder useLatestBlock() {
        this.defaultBlock = "latest";
        return this;
    }

    /**
     * Builds the RPC payload
     *
     * @return an rpc payload.
     */
    public RpcPayload build() {
        String parameters = (this.params == null) ? "" : "0x" + this.params;
        String block = (this.defaultBlock == null) ? "" : this.defaultBlock;

        return new RpcPayload(this.method, parameters, block);
    }
}
