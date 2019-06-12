package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.tests.contracts.avm.Caller;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Simple smoke test using eth_sendSignedTransaction (deployment, method call) and eth_call
 * on an AVM contract.
 */
@RunWith(SequentialRunner.class)
public class AvmCallerTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.AvmCallerTest");

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private PreminedAccount preminedAccount2 = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void test() throws Exception {
        // build contract deployment Tx
        TransactionResult deploy = RawTransaction.buildAndSignAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            new CodeAndArguments(
                JarBuilder.buildJarForMainAndClasses(Caller.class), new byte[0])
                .encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO /* amount */);
        if(! deploy.isSuccess()) {
            throw new IllegalStateException("failed to construct the deployment tx");
        }

        // send contract deployment Tx
        TransactionReceipt deployReceipt = sendTransaction(deploy.getTransaction());
        assertThat("expected address of deployed contract to be present in receipt of deployment tx",
            deployReceipt.getAddressOfDeployedContract().isPresent(), is(true));
        Address contract = deployReceipt.getAddressOfDeployedContract().get();

        // check caller1
        byte[] result = rpc.call(new Transaction(preminedAccount.getAddress(), contract, null));
        assertThat("caller1 return from avm",
            Arrays.equals(result, preminedAccount.getAddress().getAddressBytes()),
            is(true));

        // check caller2
        result = rpc.call(new Transaction(preminedAccount2.getAddress(), contract, null));
        assertThat("caller1 return from avm",
            Arrays.equals(result, preminedAccount2.getAddress().getAddressBytes()),
            is(true));

        // no caller case
        result = rpc.call(new Transaction(contract, null));
        assertThat("default caller (all 0's) return from avm",
            Arrays.equals(result, new byte[32]),
            is(true));

    }

    private TransactionReceipt sendTransaction(RawTransaction transaction) throws InterruptedException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        log.log("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        log.log("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get();
        assertTrue(listenResult.eventWasObserved());
        log.log("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }
}
