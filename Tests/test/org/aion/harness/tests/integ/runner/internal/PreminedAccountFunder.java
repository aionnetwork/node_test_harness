package org.aion.harness.tests.integ.runner.internal;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.event.PrepackagedLogEvents;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.integ.runner.exception.UnexpectedTestRunnerException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

/**
 * This class is used by our custom runners to send funds from the real premined account to one of
 * the faked-up {@link PreminedAccount} addresses we give to the test.
 *
 * This class should be invoked once per test.
 */
public final class PreminedAccountFunder {
    private static final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private final Object nonceLock = new Object();

    private final TestNodeManager nodeManager;
    private final PrepackagedLogEvents prepackagedLogEvents;
    private final PrivateKey preminedAccount;
    private final RPC rpc;
    private BigInteger currentNonce = null;

    public PreminedAccountFunder(TestNodeManager nodeManager, PrepackagedLogEvents prepackagedLogEvents) {
        this.nodeManager = nodeManager;
        this.prepackagedLogEvents = prepackagedLogEvents;
        this.rpc = RPC.newRpc("127.0.0.1", "8545");

        try {
            this.preminedAccount = PrivateKey.fromBytes(Hex.decodeHex(PREMINED_KEY));
        } catch (DecoderException | InvalidKeySpecException e) {
            // Note we know the premined key is good, we should never hit this.
            throw new UnexpectedTestRunnerException("Failed to get the private key of the real premined account!", e);
        }
    }

    /**
     * This method is used to transfer funds from the real pre-mined account to the specified account.
     *
     * This method is typically used behind the scenes by our custom runners so that they can hand
     * off faked-up pre-mined accounts to our PreminedAccount @Rule. What we really do is give those
     * accounts some funds from the real premined account.
     */
    public void fundAccount(Address address, BigInteger amount)
        throws Exception {
        // Build the transaction to transfer balance to the specified account.
        // We are assuming this transaction succeeds, so we increment nonce here too. This allows for much higher concurrent throughput.
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(
            preminedAccount,
            getCurrentNonceThenIncrement(),
            address,
            null,
            2_000_000,
            10_000_000_000L,
            amount, null);

        // Construct the 'transaction is processed' event we want to listen for.
        IEvent transactionSealed = prepackagedLogEvents.getTransactionSealedEvent(transaction);
        IEvent transactionRejected = prepackagedLogEvents.getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionRejected, transactionSealed);

        // Start listening for the transaction to get processed and send it off.
        NodeListener listener = this.nodeManager.newNodeListener();
        FutureResult<LogEventResult> future = listener.listenForEvent(transactionProcessed, 10, TimeUnit.MINUTES);
        RpcResult<ReceiptHash> sendResult = this.rpc.sendSignedTransaction(transaction);

        if (!sendResult.isSuccess()) {
            throw new UnexpectedTestRunnerException("Failed transferring " + amount + " funds from the real pre-mined account: " + sendResult.getError());
        }

        // Block until it's processed and verify it was sealed into a block and not rejected!
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);

        if(! transactionSealed.hasBeenObserved() || transactionRejected.hasBeenObserved() ) {
            throw new UnexpectedTestRunnerException("Failed transferring " + amount +
                " funds from the real pre-mined account: " + listenResult);
        }
    }

    /**
     * Grabs the current value of the nonce and then increments it.
     */
    private BigInteger getCurrentNonceThenIncrement() throws InterruptedException {
        synchronized (nonceLock) {

            // If this is the first time being invoked we fetch our nonce by asking the kernel.
            if (this.currentNonce == null) {
                RpcResult<BigInteger> nonceResult = this.rpc.getNonce(this.preminedAccount.getAddress());
                if (!nonceResult.isSuccess()) {
                    throw new IllegalStateException("Unable to get the nonce of the premined account!");
                }

                this.currentNonce = nonceResult.getResult();
            }

            BigInteger current = this.currentNonce;
            this.currentNonce = this.currentNonce.add(BigInteger.ONE);
            return current;
        }
    }
}
