package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.common.AvmContract;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.tests.integ.runner.ExcludeNodeType;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.vm.AvmFork;
import org.aion.vm.AvmUtility;
import org.aion.vm.AvmVersion;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the multi-versioned Avm in the following scenario:
 *
 * A contract is deployed using version 1 and calls into a method that is later removed in version 2.
 */
@RunWith(SequentialRunner.class)
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class MultiVersionAvmVanishingMethodTest {
    private static final BigInteger FORK_NUMBER = BigInteger.valueOf(AvmFork.BLOCK_NUMBER_FOR_VERSION2_FORK);
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void testVanishingMethod() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        // ====== 1. Deploy the contract using version 1 of the avm. =======
        TransactionReceipt receipt = deployContract(avmUtility);
        Address contract = receipt.getAddressOfDeployedContract().get();

        // Verify that we deployed using version 1 (ie. prior to the fork point).
        Assert.assertTrue(receipt.transactionWasSuccessful());
        BigInteger blockNumber = receipt.getBlockNumber();
        Assert.assertTrue(blockNumber.compareTo(FORK_NUMBER) < 0);

        // ====== 2. Call into the contract in version 1 of the avm and watch the success. =======
        receipt = callContract(avmUtility, contract);

        // Verify that we called using version 1 (ie. prior to the fork point).
        Assert.assertTrue(receipt.transactionWasSuccessful());
        blockNumber = receipt.getBlockNumber();
        Assert.assertTrue(blockNumber.compareTo(FORK_NUMBER) < 0);

        // ====== 3. Wait for the fork point to occur. =======
        waitUntilForkPoint();

        // ====== 4. Call into the contract in version 2 of the avm and watch the failure. =======
        receipt = callContract(avmUtility, contract);

        // Verify that the transaction failed and that we are past the fork point.
        Assert.assertFalse(receipt.transactionWasSuccessful());
        blockNumber = receipt.getBlockNumber();
        Assert.assertTrue(blockNumber.compareTo(FORK_NUMBER) >= 0);
    }

    /**
     * Blocks untilt he fork point has passed.
     */
    private void waitUntilForkPoint() throws InterruptedException {
        boolean forkPointHit = false;

        System.out.println("Waiting for avm fork point at block number " + AvmFork.BLOCK_NUMBER_FOR_VERSION2_FORK + " before proceeding...");
        while (!forkPointHit) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(10));

            RpcResult<Long> blockNumberResult = rpc.blockNumber();
            Assert.assertTrue(blockNumberResult.isSuccess());
            System.out.println("Current block number: " + blockNumberResult.getResult());

            forkPointHit = blockNumberResult.getResult() >= AvmFork.BLOCK_NUMBER_FOR_VERSION2_FORK;
        }
        System.out.println("Fork point hit! Proceeding with test...");
    }

    private TransactionReceipt deployContract(AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        TransactionResult result = RawTransaction.buildAndSignAvmCreateTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getAndIncrementNonce(), produceJarBytes(avmUtility), ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO);
        Assert.assertTrue(result.isSuccess());
        return sendTransaction(result.getTransaction());
    }

    private TransactionReceipt callContract(AvmUtility avmUtility, Address contract) throws InterruptedException, TimeoutException {
        byte[] data = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1).encodeOneString("invokeVanishingMethod").toBytes();
        TransactionResult result = RawTransaction.buildAndSignGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getAndIncrementNonce(), contract, data, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO);
        Assert.assertTrue(result.isSuccess());
        return sendTransaction(result.getTransaction());
    }

    private TransactionReceipt sendTransaction(RawTransaction transaction) throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);

        FutureResult<LogEventResult> future = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        Assert.assertTrue(listenResult.eventWasObserved());

        // Verify it was sealed and not rejected.
        Assert.assertFalse(transactionIsRejected.hasBeenObserved());
        Assert.assertTrue(transactionIsSealed.hasBeenObserved());
        System.out.println("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);

        return receiptResult.getResult();
    }

    private byte[] produceJarBytes(AvmUtility avmUtility) {
        return avmUtility.produceAvmJarBytes(AvmVersion.VERSION_1, AvmContract.HARNESS_VANISHING_METHOD);
    }
}
