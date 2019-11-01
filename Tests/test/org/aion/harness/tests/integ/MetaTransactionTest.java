package org.aion.harness.tests.integ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import main.SignedInvokableTransactionBuilder;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.avm.userlib.abi.ABIToken;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.Assertions;
import org.aion.harness.tests.contracts.avm.MetaTransactionProxy;
import org.aion.harness.tests.integ.runner.ExcludeNodeType;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
public class MetaTransactionTest {
    private final long ENERGY_PRICE = 10_010_020_345L;

    private final byte[] codeAndArgsForProxy =
        new CodeAndArguments(
            JarBuilder.buildJarForMainAndClasses(MetaTransactionProxy.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0])
            .encodeToBytes();

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.AvmFailuresTest");

    private RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount proxyDeployer = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private PreminedAccount companyAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private PreminedAccount companyAccount2 = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private PreminedAccount freeloaderAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void callInline() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(contract.getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        assertEquals(BigInteger.ZERO, rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void storeAndCall() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(contract.getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction storeTransaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("store", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt storeReceipt = sendRawTransactionSynchronously(storeTransaction);
        assertTrue(storeReceipt.transactionWasSuccessful());

        SignedTransaction callTransaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ONE,
                contract,
                new ABIStreamingEncoder().encodeOneString("call").toBytes(),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt callReceipt = sendRawTransactionSynchronously(callTransaction);
        assertTrue(callReceipt.transactionWasSuccessful());

        assertEquals(BigInteger.ZERO, rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(callTransaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void createInline() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(null)
                .data(codeAndArgsForProxy)
                .executor(contract.getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("createInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        assertEquals(BigInteger.ZERO, rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void createWrongMethodCall() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(null)
                .data(codeAndArgsForProxy)
                .executor(contract.getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertFalse(receipt.transactionWasSuccessful());

        assertEquals(BigInteger.valueOf(1_000_000_000L), rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNull(aliasRpcResponse);
    }

    @Test
    public void callWrongMethodCall() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(contract.getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("createInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        assertEquals(BigInteger.ZERO, rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void badExecutorCall() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(companyAccount2.getAddress().getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertFalse(receipt.transactionWasSuccessful());

        assertEquals(BigInteger.valueOf(1_000_000_000L), rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ZERO, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNull(aliasRpcResponse);
    }

    @Test
    public void badExecutorCreate() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(null)
                .data(codeAndArgsForProxy)
                .executor(companyAccount2.getAddress().getAddressBytes())
                .value(BigInteger.valueOf(1_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("createInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertFalse(receipt.transactionWasSuccessful());

        assertEquals(BigInteger.valueOf(1_000_000_000L), rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ZERO, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNull(aliasRpcResponse);
    }

    @Test
    public void replayCall() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(new byte[32])
                .value(BigInteger.valueOf(500_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        SignedTransaction repeatTransaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount2.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt repeatReceipt = sendRawTransactionSynchronously(repeatTransaction);
        assertFalse(repeatReceipt.transactionWasSuccessful());

        assertEquals(BigInteger.valueOf(500_000_000L), rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void replayCreate() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(null)
                .data(codeAndArgsForProxy)
                .executor(contract.getAddressBytes())
                .value(BigInteger.valueOf(500_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("createInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        SignedTransaction repeatTransaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount2.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("createInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt repeatReceipt = sendRawTransactionSynchronously(repeatTransaction);
        assertFalse(repeatReceipt.transactionWasSuccessful());

        assertEquals(BigInteger.valueOf(500_000_000L), rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void invokableWithDuplicateNonce() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(new byte[32])
                .value(BigInteger.valueOf(500_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        byte[] repeatInnerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(freeloaderAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ZERO)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(new byte[32])
                .value(BigInteger.valueOf(0))
                .buildSignedInvokableTransaction();

        SignedTransaction repeatTransaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount2.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", repeatInnerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt repeatReceipt = sendRawTransactionSynchronously(repeatTransaction);
        assertFalse(repeatReceipt.transactionWasSuccessful());

        assertEquals(BigInteger.valueOf(500_000_000L), rpc.getBalance(freeloaderAccount.getAddress()).getResult());
        assertEquals(BigInteger.ONE, rpc.getNonce(freeloaderAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    @Test
    public void metaAndInvokableSameAccount() throws Exception {

        Address contract = deployProxy();

        byte[] innerTx =
            new SignedInvokableTransactionBuilder()
                .privateKey(companyAccount.getPrivateKey().getPrivateKeyBytes())
                .senderNonce(BigInteger.ONE)
                .destination(new byte[32])
                .data(new byte[0])
                .executor(new byte[32])
                .value(BigInteger.valueOf(500_000_000_000_000_000L))
                .buildSignedInvokableTransaction();

        SignedTransaction transaction =
            SignedTransaction.newGeneralTransaction(
                companyAccount.getPrivateKey(),
                BigInteger.ZERO,
                contract,
                encodeCallByteArray("callInline", innerTx),
                2_000_000L,
                ENERGY_PRICE,
                BigInteger.ZERO,
                null);

        TransactionReceipt receipt = sendRawTransactionSynchronously(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        assertTrue(500_000_000_000_000_000L > rpc.getBalance(companyAccount.getAddress()).getResult().longValue());
        assertEquals(BigInteger.TWO, rpc.getNonce(companyAccount.getAddress()).getResult());

        byte[] hashedInvokable = SignedInvokableTransactionBuilder.getTransactionHashOfSignedTransaction(innerTx);

        String aliasRpcResponse = rpc.getTransactionByHash(hashedInvokable);
        String metaRpcResponse = rpc.getTransactionByHash(transaction.getTransactionHash());
        assertNotNull(metaRpcResponse);
        assertNotNull(aliasRpcResponse);
        assertEquals(aliasRpcResponse, metaRpcResponse);
    }

    private Address deployProxy() throws Exception {

        SignedTransaction deployment = SignedTransaction.newAvmCreateTransaction(
            proxyDeployer.getPrivateKey(),
            BigInteger.ZERO,
            codeAndArgsForProxy,
            5_000_000L,
            ENERGY_PRICE,
            BigInteger.ZERO,
            null);

        // send contract deployment Tx
        TransactionReceipt deployReceipt = sendRawTransactionSynchronously(deployment);
        assertTrue(deployReceipt.getAddressOfDeployedContract().isPresent());
        assertTrue(deployReceipt.transactionWasSuccessful());
        return deployReceipt.getAddressOfDeployedContract().get();
    }

    private TransactionReceipt sendRawTransactionSynchronously(SignedTransaction rawTransaction)
        throws InterruptedException, TimeoutException {
        // Capture the event for asynchronous waiting.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(rawTransaction);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);

        // Send the transaction.
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(rawTransaction);
        Assertions.assertRpcSuccess(sendResult);

        // Wait for this to be mined.
        waitForEvent(future);

        // Get the receipt (depends on it being mined).
        ReceiptHash hash = sendResult.getResult();
        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        Assertions.assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }

    private void waitForEvent(FutureResult<LogEventResult> future)
        throws InterruptedException, TimeoutException {
        log.log("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        Assert.assertTrue(listenResult.eventWasObserved());
        log.log("Transaction was sealed into a block.");
    }

    private static byte[] encodeCallByteArray(String methodName, byte[] arg) {
        return new ABIStreamingEncoder()
            .encodeOneString(methodName)
            .encodeOneByteArray(arg)
            .toBytes();
    }
}
