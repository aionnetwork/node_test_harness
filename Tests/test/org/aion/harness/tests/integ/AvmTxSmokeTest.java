package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.avm.ByteArrayHolder;
import org.aion.harness.tests.integ.runner.ExcludeNodeType;
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
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class AvmTxSmokeTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.AvmTxSmokeTest");

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void test() throws Exception {
        // build contract deployment Tx
        SignedTransaction transaction = SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            new CodeAndArguments(
                JarBuilder.buildJarForMainAndClasses(ByteArrayHolder.class), new byte[0])
                .encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, /* amount */
            null);

        // send contract deployment Tx
        TransactionReceipt deployReceipt = sendTransaction(transaction);
        assertThat("expected address of deployed contract to be present in receipt of deployment tx",
            deployReceipt.getAddressOfDeployedContract().isPresent(), is(true));
        Address contract = deployReceipt.getAddressOfDeployedContract().get();

        // check initial state of deployed contract
        byte[] result = this.rpc.call(new Transaction(contract, null));
        assertThat("initial state of deployed contract",
            Arrays.equals(result, new byte[] { 0x1, 0x2 }),
            is(true));

        this.preminedAccount.incrementNonce();

        // set "data" field of the contract
        byte[] newData1 = new byte[] { 0x21, 0x00, 0x01, 0x61 };
        SignedTransaction tx1 =
            SignedTransaction.newGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getNonce(), contract,
                newData1, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO, /* amount */
                null);
        TransactionReceipt tx1Receipt = sendTransaction(tx1);
        assertThat(tx1Receipt, is(not(nullValue()))); // can get more rigourous with this

        // check state of deployed contract after tx1
        byte[] resultAfterTx1 = this.rpc.call(new Transaction(contract, null));
        assertThat("data field of contract should have changed",
            Arrays.equals(resultAfterTx1, newData1),
            is(true));

        this.preminedAccount.incrementNonce();

        // set "data" field of the contract
        byte[] newData2 = new byte[] { 0x21, 0x00, 0x04, 0x41, 0x53, 0x44, 0x46 };
        SignedTransaction tx2 =
            SignedTransaction.newGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getNonce(), contract,
                newData2, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO, /* amount */
                null);
        TransactionReceipt tx2Receipt = sendTransaction(tx2);
        assertThat(tx2Receipt, is(not(nullValue()))); // can get more rigourous with this

        // check state of deployed contract after tx1
        byte[] resultAfterTx2 = this.rpc.call(new Transaction(contract, null));
        assertThat("data field of contract should have changed",
            Arrays.equals(resultAfterTx2, newData2),
            is(true));
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        log.log("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        log.log("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        log.log("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }
}
