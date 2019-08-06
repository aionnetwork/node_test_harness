package org.aion.harness.main;

import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.tools.InternalRpcResult;
import org.aion.harness.main.tools.RpcCaller;
import org.aion.harness.main.tools.RpcMethod;
import org.aion.harness.main.tools.JsonStringParser;
import org.aion.harness.main.tools.RpcPayload;
import org.aion.harness.main.tools.RpcPayloadBuilder;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.SyncStatus;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.types.internal.BlockBuilder;
import org.aion.harness.main.types.internal.TransactionReceiptBuilder;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.util.SimpleLog;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

/**
 * A class that facilitates communication with the node via the kernel's RPC server.
 *
 * This class interacts directly with the RPC endpoints in the kernel and does not go through the
 * Java or Web3 APIs, for example.
 *
 * This class is not thread-safe.
 */
public final class RPC {
    private final SimpleLog log;
    private final RpcCaller rpc;

    public RPC(String ip, String port) {
        this.log = new SimpleLog(getClass().getName());
        this.rpc = new RpcCaller(ip, port);
    }

    /**
     * Perform <code>eth_call</code> RPC method (synchronous).
     *
     * @param tx transaction to call
     * @return the bytes returned by the <code>eth_call</code>
     */
    public byte[] call(Transaction tx) throws InterruptedException {
        return sendCall(RpcMethod.CALL, tx.jsonString());
    }
    
    private byte[] sendCall(RpcMethod method, String params) throws InterruptedException {
        RpcPayload payload = new RpcPayload(method, params, "");
        
        log.log("-->" + payload.payload);
        InternalRpcResult response = rpc.call(payload, false);
        log.log(response.toString());
        System.out.println(response.toString());
        log.log("<--" + response.output);


        String rpcResult = new JsonParser().
                parse(response.output).getAsJsonObject().get("result").getAsString();
        try {
            String resultHex = rpcResult.replace("0x", "");
            return Hex.decodeHex(resultHex);
        } catch (DecoderException dx) {
            return rpcResult.getBytes();
        }
    }

    /**
     * Perform the special RPC method <code>getseed</code> (synchronous).
     *
     * @return the bytes returned by the <code>getseed</code>
     */
    public byte[] getSeed() throws InterruptedException {
        return sendCall(RpcMethod.GET_SEED, "");
    }

    /**
     * Perform the special RPC method <code>submitseed</code> (synchronous).
     *
     * @return the bytes returned by the <code>submitseed</code>, which should be the so-called "mining hash" field of a block template.
     */
    public byte[] submitSeed(byte[] seed, byte[] publicKey) throws InterruptedException {
        String seedString = "\"0x" + Hex.encodeHexString(seed) + '\"';
        String publicKeyString = "\"0x" + Hex.encodeHexString(publicKey) + '\"';
        System.out.println("I am sending: " + seedString + ", " + publicKeyString);
        return sendCall(RpcMethod.SUBMIT_SEED, seedString + ", " + publicKeyString);
    }

    /**
     * Perform the special RPC method <code>submitseed</code> (synchronous).
     *
     * @return the bytes returned by the <code>submitseed</code>, which should be the so-called "mining hash" field of a block template.
     */
    public byte[] submitSignature(byte[] signature, byte[] hash) throws InterruptedException {
        String signatureString = "\"0x" + Hex.encodeHexString(signature) + '\"';
        String hashString = "\"0x" + Hex.encodeHexString(hash) + '\"';
        return sendCall(RpcMethod.SUBMIT_SIGNATURE, signatureString + ", " + hashString);
    }

    public RpcResult<Long> blockNumber() throws InterruptedException {
        RpcPayload payload = new RpcPayloadBuilder().method(RpcMethod.BLOCK_NUMBER).build();

        log.log("-->" + payload.payload);
        InternalRpcResult internalResult = rpc.call(payload, false);
        log.log("<--" + internalResult.output);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            // This should never happen.
            if (result == null) {
                throw new IllegalStateException("No 'result' content to parse from: " + internalResult.output);
            }

            String rpcResult = new JsonParser().
                parse(internalResult.output).getAsJsonObject().get("result").getAsString();

            return RpcResult.successful(
                Long.parseLong(rpcResult, 10),
                internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                TimeUnit.NANOSECONDS);

        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    /**
     * Sends the specified transactions to the node.
     *
     * These calls are asynchronous, and, as such, the returned list of receipt hashes will not
     * correspond to receipts until the corresponding transactions have been fully processed.
     *
     * The returned list is such that the i'th result corresponds to the i'th transaction in the
     * input list.
     *
     * The returned bulk result will be unsuccessful if at least one sendTransaction call failed.
     *
     * Displays the I/O of the attempts to hit the RPC endpoint.
     *
     * @param transactions The transactions to send.
     * @return the results.
     */
    public List<RpcResult<ReceiptHash>> sendTransactionsVerbose(List<RawTransaction> transactions) throws InterruptedException {
        if (transactions == null) {
            throw new NullPointerException("Cannot send null transactions.");
        }

        List<RpcResult<ReceiptHash>> results = new ArrayList<>();

        for (RawTransaction transaction : transactions) {
            results.add(sendTransactionVerbose(transaction));
        }

        return results;
    }

    /**
     * Sends the specified transactions to the node.
     *
     * These calls are asynchronous, and, as such, the returned list of receipt hashes will not
     * correspond to receipts until the corresponding transactions have been fully processed.
     *
     * The returned list is such that the i'th result corresponds to the i'th transaction in the
     * input list.
     *
     * The returned bulk result will be unsuccessful if at least one sendTransaction call failed.
     *
     * @param transactions The transactions to send.
     * @return the results.
     */
    public List<RpcResult<ReceiptHash>> sendTransactions(List<RawTransaction> transactions) throws InterruptedException {
        if (transactions == null) {
            throw new NullPointerException("Cannot send null transactions.");
        }

        List<RpcResult<ReceiptHash>> results = new ArrayList<>();

        for (RawTransaction transaction : transactions) {
            results.add(sendTransaction(transaction));
        }

        return results;
    }

    /**
     * Returns a list of all the blocks whose block numbers are the specified numbers in the input
     * list.
     *
     * The returned list will have the same size as {@code numbers} and the block at index i in the
     * returned list will have a block number equal to the number at index i in the input list.
     *
     * Displays the I/O of the attempts to hit the RPC endpoint.
     *
     * @param numbers the block numbers to fetch.
     * @return the blocks.
     */
    public List<RpcResult<Block>> getBlocksByNumberVerbose(List<BigInteger> numbers) throws InterruptedException {
        if (numbers == null) {
            throw new NullPointerException("Cannot get blocks from a null list of numbers.");
        }

        List<RpcResult<Block>> results = new ArrayList<>();

        for (BigInteger number : numbers) {
            results.add(getBlockByNumberVerbose(number));
        }

        return results;
    }

    /**
     * Returns a list of all the blocks whose block numbers are the specified numbers in the input
     * list.
     *
     * The returned list will have the same size as {@code numbers} and the block at index i in the
     * returned list will have a block number equal to the number at index i in the input list.
     *
     * @param numbers the block numbers to fetch.
     * @return the blocks.
     */
    public List<RpcResult<Block>> getBlocksByNumber(List<BigInteger> numbers) throws InterruptedException {
        if (numbers == null) {
            throw new NullPointerException("Cannot get blocks from a null list of numbers.");
        }

        List<RpcResult<Block>> results = new ArrayList<>();

        for (BigInteger number : numbers) {
            results.add(getBlockByNumber(number));
        }

        return results;
    }

    /**
     * Returns a list of rpc results that, if successful, will hold the transaction receipt
     * corresponding to the provided receipt hashes, for each of the provided hashes.
     *
     * The returned list will be such that the i'th result corresponds to the i'th hash in the input
     * list.
     *
     * The returned result will be unsuccessful if at least one of the getTransactionReceipt calls
     * fails.
     *
     * Displays the I/O of the attempts to hit the RPC endpoint.
     *
     * @param receiptHashes The receipt hashes.
     * @return a list of results.
     */
    public List<RpcResult<TransactionReceipt>> getTransactionReceiptsVerbose(List<ReceiptHash> receiptHashes) throws InterruptedException {
        if (receiptHashes == null) {
            throw new NullPointerException("Cannot get transaction receipts for a null list of hashes.");
        }

        List<RpcResult<TransactionReceipt>> results = new ArrayList<>();

        for (ReceiptHash receiptHash : receiptHashes) {
            results.add(getTransactionReceiptVerbose(receiptHash));
        }

        return results;
    }

    /**
     * Returns a list of rpc results that, if successful, will hold the transaction receipt
     * corresponding to the provided receipt hashes, for each of the provided hashes.
     *
     * The returned list will be such that the i'th result corresponds to the i'th hash in the input
     * list.
     *
     * The returned result will be unsuccessful if at least one of the getTransactionReceipt calls
     * fails.
     *
     * @param receiptHashes The receipt hashes.
     * @return a list of results.
     */
    public List<RpcResult<TransactionReceipt>> getTransactionReceipts(List<ReceiptHash> receiptHashes) throws InterruptedException {
        if (receiptHashes == null) {
            throw new NullPointerException("Cannot get transaction receipts for a null list of hashes.");
        }

        List<RpcResult<TransactionReceipt>> results = new ArrayList<>();

        for (ReceiptHash receiptHash : receiptHashes) {
            results.add(getTransactionReceipt(receiptHash));
        }

        return results;
    }

    /**
     * Returns a list of the balances of the specified addresses if their results were successful.
     *
     * The returned list is such that the i'th rpc result corresponds to the i'th address in the
     * input list.
     *
     * The returned result is unsuccessful if at least one of the getBalance calls fails.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * @param addresses The addresses whose balances are to be queried.
     * @return the balances of the addresses.
     */
    public List<RpcResult<BigInteger>> getBalancesVerbose(List<Address> addresses) throws InterruptedException {
        if (addresses == null) {
            throw new NullPointerException("Cannot get the balances of a null list of addresses.");
        }

        List<RpcResult<BigInteger>> results = new ArrayList<>();

        for (Address address : addresses) {
            results.add(getBalanceVerbose(address));
        }

        return results;
    }

    /**
     * Returns a list of the balances of the specified addresses if their results were successful.
     *
     * The returned list is such that the i'th rpc result corresponds to the i'th address in the
     * input list.
     *
     * The returned result is unsuccessful if at least one of the getBalance calls fails.
     * @param addresses The addresses whose balances are to be queried.
     * @return the balances of the addresses.
     */
    public List<RpcResult<BigInteger>> getBalances(List<Address> addresses) throws InterruptedException {
        if (addresses == null) {
            throw new NullPointerException("Cannot get the balances of a null list of addresses.");
        }

        List<RpcResult<BigInteger>> results = new ArrayList<>();

        for (Address address : addresses) {
            results.add(getBalance(address));
        }

        return results;
    }

    /**
     * Returns a list of the nonces of all the specified addresses if their results are successful.
     *
     * The returned list is such that the i'th result corresponds to the i'th address in the input
     * list.
     *
     * Displays the I/O of the attempts to hit the RPC endpoint.
     *
     * @param addresses The addresses whose nonces are to be queried.
     * @return the nonces of the addresses.
     */
    public List<RpcResult<BigInteger>> getNoncesVerbose(List<Address> addresses) throws InterruptedException {
        if (addresses == null) {
            throw new NullPointerException("Cannot get nonces from a null list of addresses.");
        }

        List<RpcResult<BigInteger>> results = new ArrayList<>();

        for (Address address : addresses) {
            results.add(getNonceVerbose(address));
        }

        return results;
    }

    /**
     * Returns a list of the nonces of all the specified addresses if their results are successful.
     *
     * The returned list is such that the i'th result corresponds to the i'th address in the input
     * list.
     *
     * @param addresses The addresses whose nonces are to be queried.
     * @return the nonces of the addresses.
     */
    public List<RpcResult<BigInteger>> getNonces(List<Address> addresses) throws InterruptedException {
        if (addresses == null) {
            throw new NullPointerException("Cannot get nonces from a null list of addresses.");
        }

        List<RpcResult<BigInteger>> results = new ArrayList<>();

        for (Address address : addresses) {
            results.add(getNonce(address));
        }

        return results;
    }

    /**
     * Sends the specified transaction to the node.
     *
     * This call is asynchronous, and as such, the returned receipt hash will not correspond to a
     * receipt until the transaction has been fully processed.
     *
     * @param transaction The transaction to send.
     * @return the result of this attempt to send the transaction.
     */
    public RpcResult<ReceiptHash> sendTransaction(RawTransaction transaction) throws InterruptedException {
        return callSendTransaction(transaction, false);
    }

    /**
     * Sends the specified transaction to the node.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * This call is asynchronous, and as such, the returned receipt hash will not correspond to a
     * receipt until the transaction has been fully processed.
     *
     * @param transaction The transaction to send.
     * @return the result of this attempt to send the transaction.
     */
    public RpcResult<ReceiptHash> sendTransactionVerbose(RawTransaction transaction) throws InterruptedException {
        return callSendTransaction(transaction, true);
    }

    /**
     * Returns the block whose number is the specified number, if such a block exists.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * @param number The block number.
     * @return the result of the attempt to get the block.
     */
    public RpcResult<Block> getBlockByNumberVerbose(BigInteger number) throws InterruptedException {
        return callGetBlockByNumber(number, true);
    }

    /**
     * Returns the block whose number is the specified number, if such a block exists.
     *
     * @param number The block number.
     * @return the result of the attempt to get the block.
     */
    public RpcResult<Block> getBlockByNumber(BigInteger number) throws InterruptedException {
        return callGetBlockByNumber(number, false);
    }

    /**
     * Returns the transaction receipt whose hash is the specified receipt hash.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * Note that a receipt will not become available until a transaction has been sealed into a
     * block.
     *
     * @param receiptHash The receipt hash of the receipt to get.
     * @return the result of this attempt to get the transaction receipt.
     */
    public RpcResult<TransactionReceipt> getTransactionReceiptVerbose(ReceiptHash receiptHash) throws InterruptedException {
        return callGetTransactionReceipt(receiptHash, true);
    }

    /**
     * Returns the transaction receipt whose hash is the specified receipt hash.
     *
     * Note that a receipt will not become available until a transaction has been sealed into a
     * block.
     *
     * @param receiptHash The receipt hash of the receipt to get.
     * @return the result of this attempt to get the transaction receipt.
     */
    public RpcResult<TransactionReceipt> getTransactionReceipt(ReceiptHash receiptHash) throws InterruptedException {
        return callGetTransactionReceipt(receiptHash, false);
    }

    /**
     * Returns the balance of the specified address.
     *
     * @param address The address whose balance is to be queried.
     * @return the result of the call.
     */
    public RpcResult<BigInteger> getBalance(Address address) throws InterruptedException {
        return callGetBalance(address, false);
    }

    /**
     * Returns the balance of the specified address.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * @param address The address whose balance is to be queried.
     * @return the result of the call.
     */
    public RpcResult<BigInteger> getBalanceVerbose(Address address) throws InterruptedException {
        return callGetBalance(address, true);
    }

    /**
     * Returns the nonce of the specified address.
     *
     * @param address The address whose nonce is to be queried.
     * @return the result of the call.
     */
    public RpcResult<BigInteger> getNonce(Address address) throws InterruptedException {
        return callGetNonce(address, false);
    }

    /**
     * Returns the nonce of the specified address.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * @param address The address whose nonce is to be queried.
     * @return the result of the call.
     */
    public RpcResult<BigInteger> getNonceVerbose(Address address) throws InterruptedException {
        return callGetNonce(address, true);
    }

    /**
     * Returns the syncing status of the node.
     *
     * Note that the node will be considered as attempting to connect to the network if it has zero
     * peers. In the case of a private network, this means that this method will always time out.
     * Therefore, this method should only be called on nodes that are connected to / attempting to
     * connect to public networks.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * @return the result of the call.
     */
    public RpcResult<SyncStatus> getSyncingStatusVerbose() throws InterruptedException {
        return callSyncing(true);
    }

    /**
     * Returns the syncing status of the node.
     *
     * Note that the node will be considered as attempting to connect to the network if it has zero
     * peers. In the case of a private network, this means that this method will always time out.
     * Therefore, this method should only be called on nodes that are connected to / attempting to
     * connect to public networks.
     *
     * @return the result of the call.
     */
    public RpcResult<SyncStatus> getSyncingStatus() throws InterruptedException {
        return callSyncing(false);
    }

    /**
     * Blocks until the node has finished syncing with the rest of the network, or until the request
     * times out.
     *
     * Technically speaking, a node never finishes syncing with the network unless it is always
     * at the top block and always the first to mine the next block. However, once at the top of
     * the chain, a node is in a relatively stable position with regards to the network. Therefore
     * this method considers a node in sync with the network if it is within 5 blocks of the top
     * of the chain.
     *
     * This method will periodically print out an update as to the current status of the sync.
     *
     * @param timeout The total amount of time to wait for syncing.
     * @param timeoutUnit The time units of the timeout quantity.
     * @return  the result of this event.
     */
    public Result waitForSyncToComplete(long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout value was negative: " + timeout);
        }
        if (timeoutUnit == null) {
            throw new IllegalArgumentException("Cannot specify a null time unit.");
        }

        long currentTimeInNanos = System.nanoTime();
        long deadlineInNanos = currentTimeInNanos + timeoutUnit.toNanos(timeout);

        RpcResult<SyncStatus> syncStatus = getSyncingStatus();
        if (!syncStatus.isSuccess()) {
            return Result.unsuccessfulDueTo(syncStatus.getError());
        }

        while ((currentTimeInNanos < deadlineInNanos) && (syncStatus.getResult().isSyncing())) {
            // Log the current status.
            SyncStatus status = syncStatus.getResult();
            broadcastSyncUpdate(status.isWaitingToConnect(), status.getSyncCurrentBlockNumber(), status.getHighestBlockNumber());

            // Sleep for the specified delay, unless there is less time remaining until the
            // deadline, then sleep only until the deadline.
            long deadlineInMillis = TimeUnit.NANOSECONDS.toMillis(deadlineInNanos - currentTimeInNanos);
            long delayInMillis = TimeUnit.SECONDS.toMillis(30);
            Thread.sleep(Math.min(deadlineInMillis, delayInMillis));

            // Update the status.
            syncStatus = getSyncingStatus();
            if (!syncStatus.isSuccess()) {
                return Result.unsuccessfulDueTo(syncStatus.getError());
            }

            currentTimeInNanos = System.nanoTime();
        }

        // We either timed out or finished syncing.
        if (currentTimeInNanos >= deadlineInNanos) {
            return Result.unsuccessfulDueTo("Timed out waiting for sync to finish.");
        } else {
            return Result.successful();
        }
    }

    private void broadcastSyncUpdate(boolean waitingToConnect, BigInteger currentBlock, BigInteger highestBlock) {
        if (waitingToConnect) {
            log.log(Assumptions.LOGGER_BANNER + "Sync Progress = { waiting to connect to peers }");
        } else {
            log.log(Assumptions.LOGGER_BANNER + "Sync Progress = { At block: "
                + NumberFormat.getIntegerInstance().format(currentBlock)
                + " of " + NumberFormat.getIntegerInstance().format(highestBlock) + " }");
        }
    }

    private RpcResult<Block> callGetBlockByNumber(BigInteger number, boolean verbose) throws InterruptedException {
        if (number == null) {
            throw new NullPointerException("Cannot call getBlockByNumber using null number.");
        }

        RpcPayload payload = new RpcPayloadBuilder()
            .method(RpcMethod.GET_BLOCK_BY_NUMBER)
            .params(number.toString(16))
            .useLatestBlock()
            .build();

        InternalRpcResult internalResult = this.rpc.call(payload, verbose);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            if (result == null) {
                return RpcResult.unsuccessful("No block exists whose block number is: " + number);
            }

            try {
                Block block = new BlockBuilder().buildFromJsonString(result);
                return RpcResult.successful(
                    block,
                    internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                    TimeUnit.NANOSECONDS);
            } catch (DecoderException e) {
                return RpcResult.unsuccessful(e.toString());
            }

        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    private RpcResult<ReceiptHash> callSendTransaction(RawTransaction transaction, boolean verbose) throws InterruptedException {
        if (transaction == null) {
            throw new IllegalArgumentException("Cannot send a null transaction.");
        }

        RpcPayload payload = new RpcPayloadBuilder()
            .method(RpcMethod.SEND_RAW_TRANSACTION)
            .params(Hex.encodeHexString(transaction.getSignedTransactionBytes()))
            .build();

        log.log("-->" + payload.payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        log.log("<--" + internalResult.output);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            if (result == null) {
                return RpcResult.unsuccessful("No receipt hash was returned, transaction was likely rejected.");
            }
            
            try {
                return RpcResult.successful(
                    new ReceiptHash(Hex.decodeHex(result)),
                    internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                    TimeUnit.NANOSECONDS);

            } catch (DecoderException e) {
                return RpcResult.unsuccessful(e.toString());
            }
        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    private RpcResult<BigInteger> callGetBalance(Address address, boolean verbose) throws InterruptedException {
        if (address == null) {
            throw new IllegalArgumentException("Cannot get balance of a null address.");
        }

        RpcPayload payload = new RpcPayloadBuilder()
            .method(RpcMethod.GET_BALANCE)
            .params(Hex.encodeHexString(address.getAddressBytes()))
            .useLatestBlock()
            .build();

        InternalRpcResult internalResult = this.rpc.call(payload, verbose);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            // This should never happen.
            if (result == null) {
                throw new IllegalStateException("No 'result' content to parse from: " + internalResult.output);
            }

            return RpcResult.successful(
                new BigInteger(result, 16),
                internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                TimeUnit.NANOSECONDS);

        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    private RpcResult<BigInteger> callGetNonce(Address address, boolean verbose) throws InterruptedException {
        if (address == null) {
            throw new IllegalArgumentException("Cannot get nonce of a null address.");
        }

        RpcPayload payload = new RpcPayloadBuilder()
            .method(RpcMethod.GET_NONCE)
            .params(Hex.encodeHexString(address.getAddressBytes()))
            .useLatestBlock()
            .build();

        log.log("-->" + payload.payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        log.log("<--" + internalResult.output);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            // This should never happen.
            if (result == null) {
                throw new IllegalStateException("No 'result' content to parse from: " + internalResult.output);
            }

            return RpcResult.successful(
                new BigInteger(result, 16),
                internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                TimeUnit.NANOSECONDS);

        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    private RpcResult<TransactionReceipt> callGetTransactionReceipt(ReceiptHash receiptHash, boolean verbose) throws InterruptedException {
        if (receiptHash == null) {
            throw new NullPointerException("Cannot get a receipt from a null receipt hash.");
        }

        RpcPayload payload = new RpcPayloadBuilder()
            .method(RpcMethod.GET_TRANSACTION_RECEIPT)
            .params(Hex.encodeHexString(receiptHash.getHash()))
            .build();

        log.log("-->" + payload.payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        log.log("<--" + internalResult.output);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            if (result == null) {
                return RpcResult.unsuccessful("No transaction receipt was returned, the transaction may still be processing.");
            }

            try {
                TransactionReceipt receipt = new TransactionReceiptBuilder().buildFromJsonString(result);
                return RpcResult.successful(
                    receipt,
                    internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                    TimeUnit.NANOSECONDS);

            } catch (DecoderException e) {
                return RpcResult.unsuccessful(e.toString());
            }

        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    private RpcResult<SyncStatus> callSyncing(boolean verbose) throws InterruptedException {
        RpcPayload payload = new RpcPayloadBuilder()
            .method(RpcMethod.IS_SYNCED)
            .build();

        InternalRpcResult internalResult = this.rpc.call(payload, verbose);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");

            // This should never happen.
            if (result == null) {
                throw new IllegalStateException("No 'result' content to parse from: " + internalResult.output);
            }

            if (result.equals("false")) {
                return RpcResult.successful(
                    SyncStatus.notSyncing(),
                    internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                    TimeUnit.NANOSECONDS);

            } else {
                JsonStringParser statusParser = new JsonStringParser(result);
                String startBlock = statusParser.attributeToString("startingBlock");
                String currBlock = statusParser.attributeToString("currentBlock");
                String highBlock = statusParser.attributeToString("highestBlock");

                BigInteger startingBlock = (startBlock == null) ? null : new BigInteger(startBlock, 16);
                BigInteger currentBlock = (currBlock == null) ? null : new BigInteger(currBlock, 16);
                BigInteger highestBlock = (highBlock == null) ? null : new BigInteger(highBlock, 16);

                // We can tell that we haven't connected to the network yet if its highest block number is zero.
                boolean waitingToConnect = (highestBlock != null) && (highestBlock.equals(BigInteger.ZERO));

                // There is currently a bug in the kernel where it can report being at block N of N
                // yet not realize it is finished syncing yet..
                if ((currentBlock != null) && (highestBlock != null)) {
                    if (highestBlock.subtract(currentBlock).compareTo(BigInteger.valueOf(5)) < 0) {
                        return RpcResult.successful(
                            SyncStatus.notSyncing(),
                            internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                            TimeUnit.NANOSECONDS);
                    }
                }

                return RpcResult.successful(
                    SyncStatus.syncing(waitingToConnect, startingBlock, currentBlock, highestBlock),
                    internalResult.getTimeOfCall(TimeUnit.NANOSECONDS),
                    TimeUnit.NANOSECONDS);
            }

        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

}
