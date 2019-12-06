package org.aion.harness.tests.integ;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.util.TestHarnessHelper;
import org.aion.harness.result.BulkResult;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.avm.SimpleContract;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
public class AlternatingVmTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    /**
     * Tests making a bunch of Avm-Fvm alternating contract create transactions and sending them off.
     *
     * This is mostly here to verify that the BulkExecutor is able to correctly hand batches of
     * transactions off to the two vm's even when these batches consist of single transactions each.
     */
    @Test
    public void testAlternatingCreationTransactions() throws Exception {
        List<SignedTransaction> transactions = makeAlternatingAvmFvmContractCreateTransactions(50);
        sendTransactions(transactions);
    }

    private void sendTransactions(List<SignedTransaction> transactions)
        throws InterruptedException, TimeoutException {
        List<IEvent> transactionProcessedEvents = makeTransactionProcessedEventPerTransaction(transactions);

        List<FutureResult<LogEventResult>> futures = this.listener.listenForEvents(transactionProcessedEvents, 10, TimeUnit.MINUTES);

        // Send the transactions off.
        System.out.println("Sending the " + transactions.size() + " transactions...");
        List<RpcResult<ReceiptHash>> sendResults = rpc.sendSignedTransactions(transactions);
        BulkResult<ReceiptHash> bulkHashes = TestHarnessHelper.extractRpcResults(sendResults);
        assertTrue(bulkHashes.isSuccess());

        // Wait on the futures to complete and ensure we saw the transactions get sealed.
        System.out.println("Waiting for the " + transactions.size() + " transactions to process...");
        for (FutureResult<LogEventResult> future : futures) {
            LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
            assertTrue(listenResult.eventWasObserved());
        }
        System.out.println("Transactions were all sealed into blocks!");

        for (ReceiptHash hash : bulkHashes.getResults()) {
            RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
            assertTrue(receiptResult.isSuccess());
            assertTrue(receiptResult.getResult().transactionWasSuccessful());
        }
    }

    private List<IEvent> makeTransactionProcessedEventPerTransaction(List<SignedTransaction> transactions) {
        List<IEvent> events = new ArrayList<>();
        for (SignedTransaction transaction : transactions) {
            events.add(prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction));
        }
        return events;
    }

    private List<SignedTransaction> makeAlternatingAvmFvmContractCreateTransactions(int totalNum) throws DecoderException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        List<SignedTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < totalNum; i++) {
            if (i % 2 == 0) {
                transactions.add(makeAvmTransaction());
            } else {
                transactions.add(makeFvmTransaction());
            }
        }
        return transactions;
    }

    private SignedTransaction makeFvmTransaction() throws DecoderException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            null,
            getFvmDeployBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }

    private SignedTransaction makeAvmTransaction() throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(SimpleContract.class), new byte[0]).encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }

    /**
     * This is the binary output of solc for the solidity program located in test_resources/Bytes16Holder.sol
     */
    private static byte[] getFvmDeployBytes() throws DecoderException {
        String bytes = "605060405234156100105760006000fd5b5b6101026000600050819090600019169055505b610029565b60e0806100376000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680636d4ce63c14603b578063ac6effc214606a576035565b60006000fd5b341560465760006000fd5b604c608f565b60405180826000191660001916815260100191505060405180910390f35b341560755760006000fd5b608d60048080356000191690601001909190505060a0565b005b60006000600050549050609d565b90565b806000600050819090600019169055505b505600a165627a7a72305820d6585e5cd5b612562558cd595d992e3cde87a53ed8765ab62c9662cca20d914a0029";
        return Hex.decodeHex(bytes);
    }
}
