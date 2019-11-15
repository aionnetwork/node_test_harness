package org.aion.harness.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.kernel.UnsignedTransaction;
import org.aion.harness.main.tools.InternalRpcResult;
import org.aion.harness.main.tools.RpcCaller;
import org.aion.harness.main.tools.RpcMethod;
import org.aion.harness.main.tools.JsonStringParser;
import org.aion.harness.main.tools.RpcPayload;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.types.MinedBlockSolution;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.SyncStatus;
import org.aion.harness.main.types.TransactionLog;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.types.internal.BlockBuilder;
import org.aion.harness.main.types.internal.TransactionLogBuilder;
import org.aion.harness.main.types.internal.TransactionReceiptBuilder;
import org.aion.harness.main.types.BlockTemplate;
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
    private final SimpleLog logger;
    private final RpcCaller rpc;

    private RPC(String ip, String port, SimpleLog logger) {
        this.logger = logger;
        this.rpc = new RpcCaller(ip, port);
    }

    public static RPC newRpc(String ip, String port) {
        return new RPC(ip, port, null);
    }

    public static RPC newVerboseRpc(String ip, String port) {
        return new RPC(ip, port, new SimpleLog(RPC.class.getName()));
    }

    /**
     * Perform <code>eth_call</code> RPC method (synchronous).
     *
     * @param tx transaction to call
     * @return the bytes returned by the <code>eth_call</code>
     */
    public byte[] call(Transaction tx) throws InterruptedException {
        // Construct the payload to the rpc call (ie. the content of --data).
        String params = tx.jsonString() + ",\"latest\"";
        String payload = RpcPayload.generatePayload(RpcMethod.CALL, params);

        logMessage("-->" + payload);
        InternalRpcResult response = rpc.call(payload, false);
        logMessage("<--" + response.output);


        String rpcResult = new JsonParser().
            parse(response.output).getAsJsonObject().get("result").getAsString();
        try {
            String resultHex = rpcResult.replace("0x", "");
            return Hex.decodeHex(resultHex);
        } catch (DecoderException dx) {
            throw new IllegalStateException("eth_call result from kernel could not be hex decoded.  result was:" + rpcResult);
        }
    }

    public RpcResult<Long> blockNumber() throws InterruptedException {
        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "";
        String payload = RpcPayload.generatePayload(RpcMethod.BLOCK_NUMBER, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = rpc.call(payload, false);
        logMessage("<--" + internalResult.output);

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
     * Sends the specified signed transactions to the node.
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
    public List<RpcResult<ReceiptHash>> sendSignedTransactionsVerbose(List<SignedTransaction> transactions) throws InterruptedException {
        if (transactions == null) {
            throw new NullPointerException("Cannot send null transactions.");
        }

        List<RpcResult<ReceiptHash>> results = new ArrayList<>();

        for (SignedTransaction transaction : transactions) {
            results.add(sendSignedTransactionVerbose(transaction));
        }

        return results;
    }

    /**
     * Sends the specified signed transactions to the node.
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
    public List<RpcResult<ReceiptHash>> sendSignedTransactions(List<SignedTransaction> transactions) throws InterruptedException {
        if (transactions == null) {
            throw new NullPointerException("Cannot send null transactions.");
        }

        List<RpcResult<ReceiptHash>> results = new ArrayList<>();

        for (SignedTransaction transaction : transactions) {
            results.add(sendSignedTransaction(transaction));
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
     * Attempts to unlock the specified account using the given password for the duration specified
     * in the given units.
     *
     * Returns {@code true} if the unlock succeeded, {@code false} otherwise.
     *
     * The output of this method call will be verbose.
     *
     * @param account The account to unlock.
     * @param password The account password.
     * @param unlockDuration The duration to leave the account unlocked.
     * @param units The units that the duration are in.
     * @return the result of this attempt to unlock the account.
     */
    public RpcResult<Boolean> unlockKeystoreAccountVerbose(Address account, String password, long unlockDuration, TimeUnit units) throws InterruptedException {
        return callUnlockKeystoreAccount(account, password, units.toSeconds(unlockDuration), true);
    }

    /**
     * Attempts to unlock the specified account using the given password for the duration specified
     * in the given units.
     *
     * Returns {@code true} if the unlock succeeded, {@code false} otherwise.
     *
     * @param account The account to unlock.
     * @param password The account password.
     * @param unlockDuration The duration to leave the account unlocked.
     * @param units The units that the duration are in.
     * @return the result of this attempt to unlock the account.
     */
    public RpcResult<Boolean> unlockKeystoreAccount(Address account, String password, long unlockDuration, TimeUnit units) throws InterruptedException {
        return callUnlockKeystoreAccount(account, password, units.toSeconds(unlockDuration), false);
    }

    /**
     * Sends the specified unsigned transaction to the node. This will only work if the account has
     * already been unlocked.
     *
     * The output of this method is verbose.
     *
     * @param transaction The transaction to sign.
     * @return the result of the attempt to send the transaction.
     */
    public RpcResult<ReceiptHash> sendUnsignedTransactionVerbose(UnsignedTransaction transaction) throws InterruptedException {
        return callSendUnsignedTransaction(transaction, true);
    }

    /**
     * Sends the specified unsigned transaction to the node. This will only work if the account has
     * already been unlocked.
     *
     * @param transaction The transaction to sign.
     * @return the result of the attempt to send the transaction.
     */
    public RpcResult<ReceiptHash> sendUnsignedTransaction(UnsignedTransaction transaction) throws InterruptedException {
        return callSendUnsignedTransaction(transaction, false);
    }

    /**
     * Sends the specified signed transaction to the node.
     *
     * This call is asynchronous, and as such, the returned receipt hash will not correspond to a
     * receipt until the transaction has been fully processed.
     *
     * @param transaction The transaction to send.
     * @return the result of this attempt to send the transaction.
     */
    public RpcResult<ReceiptHash> sendSignedTransaction(SignedTransaction transaction) throws InterruptedException {
        return callSendSignedTransaction(transaction, false);
    }

    /**
     * Sends the specified signed transaction to the node.
     *
     * Displays the I/O of the attempt to hit the RPC endpoint.
     *
     * This call is asynchronous, and as such, the returned receipt hash will not correspond to a
     * receipt until the transaction has been fully processed.
     *
     * @param transaction The transaction to send.
     * @return the result of this attempt to send the transaction.
     */
    public RpcResult<ReceiptHash> sendSignedTransactionVerbose(SignedTransaction transaction) throws InterruptedException {
        return callSendSignedTransaction(transaction, true);
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
     * Requests the kernel for the transaction and block info of the transaction that ran the
     * given hash, which could be the hash of an invokable.
     *
     * Note that rpc.call must be called with verbose = false or this function will not receive
     * any output.
     *
     * @return the result of the call.
     */
    public String getTransactionByHash(byte[] hash) throws InterruptedException {
        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(hash) + "\"";
        String payload = RpcPayload.generatePayload(RpcMethod.GET_TRANSACTION_BY_HASH, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = rpc.call(payload, false);
        logMessage("<--" + internalResult.output);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            return outputParser.attributeToString("result");
        } else {
            return null;
        }
    }

    /**
     * Requests the kernel for the block template of the next block to be mined.
     *
     * Note that rpc.call must be called with verbose = false or this function will not receive
     * any output.
     *
     * @return the result of the call.
     */
    public BlockTemplate getBlockTemplate() throws InterruptedException {
        // Construct the payload to the rpc call (ie. the content of --data).
        String payload = RpcPayload.generatePayload(RpcMethod.GET_BLOCK_TEMPLATE, "");

        logMessage("-->" + payload);
        InternalRpcResult internalResult = rpc.call(payload, false);
        logMessage("<--" + internalResult.output);

        if (internalResult.success) {
            try {
                JsonStringParser outputParser = new JsonStringParser(internalResult.output);
                String result = outputParser.attributeToString("result");
                JsonStringParser innerParser = new JsonStringParser(result);
                BigInteger difficultyTarget = new BigInteger(Hex.decodeHex(innerParser.attributeToString("target")));
                byte[] headerHash = Hex.decodeHex(innerParser.attributeToString("headerHash"));

                return new BlockTemplate(difficultyTarget, headerHash);
            } catch (Exception ignored) {}
        }

        return null;
    }

    public void submitSolution(MinedBlockSolution solution) throws InterruptedException {
        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(solution.getNonce()) + "\",\"0x" + Hex.encodeHexString(solution.getSolution()) + "\",\"0x" + Hex.encodeHexString(solution.getHeaderHash()) + "\"";
        String payload = RpcPayload.generatePayload(RpcMethod.SUBMIT_BLOCK, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = rpc.call(payload, false);
        logMessage("<--" + internalResult.output);
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

    /**
     * Checks for logs generated at or after the given startingPoint, which have a first topic in the given set and which were generated by the given contract.
     * Note that the "address" filter of the RPC cannot be used since that is the target of the external transaction, not always what generated the log.
     * 
     * @param startingPoint The first block number to check for logs.
     * @param firstTopicFilterUnion The returned logs must have a first topic which is in this set (a null set will do no topic filtering).
     * @param reportingContract The address of the contract which logged the event.
     * @return the result of the filtered logs.
     */
    public RpcResult<List<TransactionLog>> getLatestFilteredLogs(long startingPoint, Set<byte[]> firstTopicFilterUnion, Address reportingContract) throws InterruptedException {
        if (startingPoint < 1) {
            throw new IllegalArgumentException("Starting point must be a positive integer.");
        }
        if (reportingContract == null) {
            throw new IllegalArgumentException("This helper expects that the address is already known.");
        }
        boolean verbose = false;

        // Construct the payload to the rpc call (ie. the content of --data).
        // For now, we will just use "latest" as the destination but non-testing cases will likely want to impose a limit on this.
        String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"fromBlock\":" + startingPoint + ",\"toBlock\":\"latest\"";
        if (null != firstTopicFilterUnion) {
            payload += ",\"topics\":[[";
            boolean addComma = false;
            if (firstTopicFilterUnion.isEmpty()) {
                throw new IllegalArgumentException("The set of first topic filters must be null or non-empty.");
            }
            for (byte[] topic : firstTopicFilterUnion) {
                if (addComma) {
                    payload += ",";
                }
                payload += "\"0x" + Hex.encodeHexString(padRight(32, topic)) + "\"";
                addComma = true;
            }
            payload += "]]";
        }
        payload += "}],\"id\":1}";

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

        // We want to go through these, filtering based on address.
        RpcResult<List<TransactionLog>> finalResult = null;
        try {
            if (internalResult.success) {
                JsonObject topLevelResponse = (JsonObject) new JsonParser().parse(internalResult.output);
                JsonArray resultArray = topLevelResponse.getAsJsonArray("result");
                // First, we need to read all the log entries.
                List<TransactionLog> logs = new ArrayList<>();
                for (JsonElement result : resultArray) {
                    JsonObject obj = (JsonObject) result;
                    TransactionLog log = new TransactionLogBuilder().buildFromJsonString(obj.toString());
                    // If this was a different address than the one we wanted to see, ignore it.
                    if (reportingContract.equals(log.address)) {
                        logs.add(log);
                    }
                }
                finalResult = RpcResult.successful(logs, internalResult.getTimeOfCall(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
            } else {
                finalResult = RpcResult.unsuccessful(internalResult.error);
            }
        } catch (Exception e) {
            // This is unexpected.
            throw new AssertionError(e);
        }
        return finalResult;
    }

    private byte[] padRight(int width, byte[] topic) {
        byte[] result = new byte[width];
        System.arraycopy(topic, 0, result, 0, topic.length);
        return result;
    }

    private void broadcastSyncUpdate(boolean waitingToConnect, BigInteger currentBlock, BigInteger highestBlock) {
        if (waitingToConnect) {
            logMessage(Assumptions.LOGGER_BANNER + "Sync Progress = { waiting to connect to peers }");
        } else {
            logMessage(Assumptions.LOGGER_BANNER + "Sync Progress = { At block: "
                + NumberFormat.getIntegerInstance().format(currentBlock)
                + " of " + NumberFormat.getIntegerInstance().format(highestBlock) + " }");
        }
    }

    private RpcResult<Block> callGetBlockByNumber(BigInteger number, boolean verbose) throws InterruptedException {
        if (number == null) {
            throw new NullPointerException("Cannot call getBlockByNumber using null number.");
        }

        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + number.toString(16) + "\", false";
        String payload = RpcPayload.generatePayload(RpcMethod.GET_BLOCK_BY_NUMBER, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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

    private RpcResult<Boolean> callUnlockKeystoreAccount(Address account, String password, long unlockDurationInSeconds, boolean verbose) throws InterruptedException {
        if (account == null) {
            throw new NullPointerException("Cannot unlock null account!");
        }
        if (password == null) {
            throw new NullPointerException("Cannot unlock account with null password!");
        }

        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(account.getAddressBytes()) + "\",\"" + password + "\",\"" + unlockDurationInSeconds + "\"";
        String payload = RpcPayload.generatePayload(RpcMethod.UNLOCK_ACCOUNT, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

        if (internalResult.success) {
            JsonStringParser outputParser = new JsonStringParser(internalResult.output);
            String result = outputParser.attributeToString("result");
            if (result == null) {
                return RpcResult.unsuccessful("No result was returned!");
            } else {
                return RpcResult.successful(Boolean.valueOf(result), internalResult.getTimeOfCall(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
            }
        } else {
            return RpcResult.unsuccessful(internalResult.error);
        }
    }

    private RpcResult<ReceiptHash> callSendUnsignedTransaction(UnsignedTransaction transaction, boolean verbose) throws InterruptedException {
        if (transaction == null) {
            throw new IllegalArgumentException("Cannot send a null transaction.");
        }

        // Construct the payload to the rpc call (ie. the content of --data).
        // If the destination is not present then we display nothing. If the data is an empty array
        // then we display '0x0' instead of the otherwise '0x'.
        byte[] transactionData = transaction.copyOfData();
        String destination = (transaction.destination == null) ? "" : ("\"to\":\"0x" + Hex.encodeHexString(transaction.destination.getAddressBytes()) + "\",");
        String data = (transactionData.length == 0) ? "\"data\":\"0x0\"," : "\"data\":\"0x" + Hex.encodeHexString(transactionData) + "\",";
        String params = "{\"from\":\"0x" + Hex.encodeHexString(transaction.sender.getAddressBytes()) + "\","
            + destination
            + "\"gas\":\"0x" + BigInteger.valueOf(transaction.energyLimit).toString(16) + "\","
            + "\"gasPrice\":\"0x" + BigInteger.valueOf(transaction.energyPrice).toString(16) + "\","
            + "\"value\":\"0x" + transaction.valueToTransfer.toString(16) + "\","
            + data
            + "\"nonce\":\"0x" + transaction.senderNonce.toString(16) + "\"}";
        String payload = RpcPayload.generatePayload(RpcMethod.SEND_TRANSACTION, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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

    private RpcResult<ReceiptHash> callSendSignedTransaction(SignedTransaction transaction, boolean verbose) throws InterruptedException {
        if (transaction == null) {
            throw new IllegalArgumentException("Cannot send a null transaction.");
        }

        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(transaction.getSignedTransactionBytes()) + "\"";
        String payload = RpcPayload.generatePayload(RpcMethod.SEND_RAW_TRANSACTION, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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

        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(address.getAddressBytes()) + "\", \"latest\"";
        String payload = RpcPayload.generatePayload(RpcMethod.GET_BALANCE, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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

        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(address.getAddressBytes()) + "\", \"latest\"";
        String payload = RpcPayload.generatePayload(RpcMethod.GET_NONCE, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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

        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "\"0x" + Hex.encodeHexString(receiptHash.getHash()) + "\"";
        String payload = RpcPayload.generatePayload(RpcMethod.GET_TRANSACTION_RECEIPT, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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
        // Construct the payload to the rpc call (ie. the content of --data).
        String params = "";
        String payload = RpcPayload.generatePayload(RpcMethod.IS_SYNCED, params);

        logMessage("-->" + payload);
        InternalRpcResult internalResult = this.rpc.call(payload, verbose);
        logMessage("<--" + internalResult.output);

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

    private void logMessage(String message) {
        if (this.logger != null) {
            this.logger.log(message);
        }
    }
}
