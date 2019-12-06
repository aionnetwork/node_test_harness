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
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
public class FvmTxSmokeTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.FvmTxSmokeTest");

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    /**
     * <code>data</code> field for deploying the contract.  This is the binary output of
     * solc for the solidity program located in test_resources/Bytes16Holder.sol
     */
    private final static byte[] DEPLOY_DATA;

    /**
     * <code>data</code> field for solidity method call on the above contract:
     *   set(0x00000000000000000000000021000161)
     */
    private final static byte[] SET_DATA_1;

    /**
     * <code>data</code> field for solidity method call on the above contract:
     *   set(0x00000000000000000021000441534446)
     */
    private final static byte[] SET_DATA_2;

    /**
     * <code>data</code> field for solidity method call on the above contract:
     *   get()
     */
    private final static byte[] GET_DATA;

    static {
        try {
            DEPLOY_DATA = Hex.decodeHex("605060405234156100105760006000fd5b5b6101026000600050819090600019169055505b610029565b60e0806100376000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680636d4ce63c14603b578063ac6effc214606a576035565b60006000fd5b341560465760006000fd5b604c608f565b60405180826000191660001916815260100191505060405180910390f35b341560755760006000fd5b608d60048080356000191690601001909190505060a0565b005b60006000600050549050609d565b90565b806000600050819090600019169055505b505600a165627a7a72305820d6585e5cd5b612562558cd595d992e3cde87a53ed8765ab62c9662cca20d914a0029");
            SET_DATA_1 = Hex.decodeHex("ac6effc200000000000000000000000021000161");
            SET_DATA_2 = Hex.decodeHex("ac6effc200000000000000000021000441534446");
            GET_DATA = Hex.decodeHex("6d4ce63c");
        } catch (DecoderException dx) {
            throw new IllegalStateException(
                "Test error: could not initialize the data to be used in the test.", dx);
        }
    }

    @Test
    public void test() throws Exception {
        // build contract deployment Tx
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            null,
            DEPLOY_DATA,
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
        byte[] result = rpc.call(new Transaction(contract, GET_DATA));
        assertThat(
            "initial state of deployed contract",
            Arrays.equals(result, new byte[] {
                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x1, 0x2
            }),
            is(true));

        this.preminedAccount.incrementNonce();

        // set "data" field of the contract
        SignedTransaction tx1 =
            SignedTransaction.newGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getNonce(), contract,
                SET_DATA_1, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO, /* amount */
                null);
        TransactionReceipt tx1Receipt = sendTransaction(tx1);
        assertThat(tx1Receipt, is(not(nullValue()))); // can get more rigourous with this

        // check state of deployed contract after tx1
        byte[] resultAfterTx1 = rpc.call(new Transaction(contract, GET_DATA));
        assertThat(
            "initial state of deployed contract",
            Arrays.equals(resultAfterTx1, new byte[] {
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x21, 0x00, 0x01, 0x61
            }),
            is(true));

        this.preminedAccount.incrementNonce();

        // set "data" field of the contract
        SignedTransaction tx2 =
            SignedTransaction.newGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getNonce(), contract,
                SET_DATA_2, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO, /* amount */
                null);
        TransactionReceipt tx2Receipt = sendTransaction(tx2);
        assertThat(tx2Receipt, is(not(nullValue()))); // can get more rigourous with this

        // check state of deployed contract after tx1
        byte[] resultAfterTx2 = rpc.call(new Transaction(contract, GET_DATA));
        assertThat(
            "initial state of deployed contract",
            Arrays.equals(resultAfterTx2, new byte[] {
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x21, 0x00, 0x04, 0x41, 0x53, 0x44, 0x46
            }),
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
