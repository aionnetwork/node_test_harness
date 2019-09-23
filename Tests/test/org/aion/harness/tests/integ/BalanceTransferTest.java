package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.avm.SimpleContract;
import org.aion.harness.tests.integ.runner.ExcludeNodeType;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class BalanceTransferTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.BalanceTransferTest");

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    /**
     * Tests making a CREATE transaction in which funds are transferred as well.
     */
    @Test
    public void testTransferBalanceToFvmContractUponCreation()
        throws Exception {
        BigInteger originalBalance = getPreminedBalance();
        BigInteger amount = BigInteger.TEN.pow(13).add(BigInteger.valueOf(2_938_652));

        // Build and send the transaction off, grab its receipt.
        SignedTransaction transaction = buildTransactionToCreateAndTransferToFvmContract(amount);
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        Address contract = createReceipt.getAddressOfDeployedContract().get();

        // Compute our expected balance and verify it matches the sender's current balance.
        BigInteger energyCost = getEnergyCost(createReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests making a CREATE transaction followed by a CALL transaction to transfer funds to a
     * non-payable function (which does not work) followed by a CALL transaction to transfer funds
     * to a payable function (which does work).
     */
    @Test
    public void testTransferBalanceToFvmContractAfterCreation()
        throws Exception {
        BigInteger amount = BigInteger.TEN.pow(11).add(BigInteger.valueOf(237_645));

        // Build and send the transaction off, grab its receipt.
        log.log("Creating the fvm contract...");
        SignedTransaction transaction = buildTransactionToCreateFvmContract();
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        // Get the contract address as well as it's & the pre-mined account's balance.
        Address contract = createReceipt.getAddressOfDeployedContract().get();
        BigInteger originalBalance = getPreminedBalance();

        this.preminedAccount.incrementNonce();

        // Attempt to transfer funds to a non-payable function in the contract.
        log.log("Attempting to transfer funds to the contract...");
        transaction = buildTransactionToTransferFundsToNonPayableFunction(contract, amount);
        TransactionReceipt transferReceipt = sendTransaction(transaction);

        // The function is not payable so we expect to not have transferred any balance.
        BigInteger energyCost = getEnergyCost(transferReceipt);
        BigInteger expectedBalance = originalBalance.subtract(energyCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(BigInteger.ZERO, getBalance(contract));

        this.preminedAccount.incrementNonce();

        // Now attempt to transfer funds to a payable function in the contract.
        log.log("Using correct (payable) method to transfer funds...");
        transaction = buildTransactionToTransferFundsToPayableFunction(contract, amount);
        transferReceipt = sendTransaction(transaction);

        // The function is payable so now we expect to have transferred the balance.
        energyCost = getEnergyCost(transferReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        expectedBalance = expectedBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests making a CREATE transaction in which funds are transferred as well.
     */
    @Test
    public void testTransferBalanceToAvmContractUponCreation()
        throws Exception {
        BigInteger originalBalance = getPreminedBalance();
        BigInteger amount = BigInteger.TEN.pow(17).add(BigInteger.valueOf(298_365_712));

        log.log("Creating the avm contract...");
        SignedTransaction transaction = buildTransactionToCreateAndTransferToAvmContract(amount);
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        Address contract = createReceipt.getAddressOfDeployedContract().get();

        // Compute our expected balance and verify it matches the sender's current balance.
        BigInteger energyCost = getEnergyCost(createReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests making a CREATE transaction followed by a CALL transaction to transfer funds to an
     * avm contract.
     */
    @Test
    public void testTransferBalanceToAvmContractAfterCreation()
        throws Exception {
        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(2_384_956));

        // Create the contract.
        log.log("Creating the avm contract...");
        SignedTransaction transaction = buildTransactionToCreateAvmContract();
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        Address contract = createReceipt.getAddressOfDeployedContract().get();

        this.preminedAccount.incrementNonce();

        // Now transfer funds to the contract.
        log.log("Transferring funds to the contract...");
        BigInteger originalBalance = getPreminedBalance();
        transaction = buildTransactionToTransferFundsToAvmContract(contract, amount);
        TransactionReceipt transferReceipt = sendCallToAvmContract(transaction);

        // Compute our expected balance and verify it matches the sender's current balance.
        BigInteger energyCost = getEnergyCost(transferReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests transferring funds to a newly created random address. This is a regular account that
     * has no code (ie. it is not a contract address).
     */
    @Test
    public void testTransferBalanceToRegularAccount()
        throws Exception {
        BigInteger amount = BigInteger.TEN.pow(16).add(BigInteger.valueOf(12_476));
        BigInteger originalBalance = getPreminedBalance();

        // Create the new account.
        Address regularAccount = PrivateKey.random().getAddress();

        // Transfer the funds.
        log.log("Transferring funds to the account...");
        SignedTransaction transaction = buildTransactionToTransferFundsToAccount(regularAccount, amount);
        TransactionReceipt transferReceipt = sendTransaction(transaction);

        // Verify that the pre-mined account traferred the balance.
        BigInteger energyCost = getEnergyCost(transferReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());

        // Verify that the regular account received the balance.
        assertEquals(amount, getBalance(regularAccount));
    }

    private BigInteger getEnergyCost(TransactionReceipt receipt) {
        return BigInteger.valueOf(receipt.getTransactionEnergyConsumed()).multiply(BigInteger.valueOf(ENERGY_PRICE));
    }

    private BigInteger getPreminedBalance() throws InterruptedException {
        RpcResult<BigInteger> balanceResult = rpc.getBalance(this.preminedAccount.getAddress());
        assertRpcSuccess(balanceResult);
        return balanceResult.getResult();
    }

    private BigInteger getBalance(Address address) throws InterruptedException {
        RpcResult<BigInteger> balanceResult = rpc.getBalance(address);
        assertRpcSuccess(balanceResult);
        return balanceResult.getResult();
    }

    private TransactionReceipt sendCallToAvmContract(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent contractPrintln = new Event("I'm a pretty dull contract.");
        IEvent event = Event.and(transactionIsSealed, contractPrintln);
        FutureResult<LogEventResult> future = listener.listenForEvent(event, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        log.log("Sending the avm call transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        log.log("Waiting for the avm call transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        log.log("Transaction was sealed into a block & we observed the println statement.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        FutureResult<LogEventResult> future = listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);

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

    private SignedTransaction buildTransactionToTransferFundsToAvmContract(Address contract, BigInteger amount) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            contract,
            new byte[]{ 0x1, 0x2, 0x3 }, // we give a non-empty array here so that we actually invoke the main method.
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount, null);
    }

    private SignedTransaction buildTransactionToTransferFundsToAccount(Address account, BigInteger amount) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            account,
            new byte[0],
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount, null);
    }

    private SignedTransaction buildTransactionToTransferFundsToPayableFunction(Address contract, BigInteger amount) throws DecoderException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            contract,
            getPayableFunctionCallEncoding(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount, null);
    }

    private SignedTransaction buildTransactionToTransferFundsToNonPayableFunction(Address contract, BigInteger amount) throws DecoderException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            contract,
            getNonPayableFunctionCallEncoding(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount, null);
    }

    private SignedTransaction buildTransactionToCreateFvmContract() throws DecoderException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        return buildTransactionToCreateAndTransferToFvmContract(BigInteger.ZERO);
    }

    private SignedTransaction buildTransactionToCreateAndTransferToFvmContract(BigInteger amount) throws DecoderException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            null,
            getFvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount, null);
    }

    private SignedTransaction buildTransactionToCreateAvmContract() throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        return buildTransactionToCreateAndTransferToAvmContract(BigInteger.ZERO);
    }

    private SignedTransaction buildTransactionToCreateAndTransferToAvmContract(BigInteger amount) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            getAvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount, null);
    }

    /**
     * Returns the bytes of an FVM contract named 'PayableConstructor'.
     *
     * See src/org/aion/harness/tests/contracts/fvm/PayableConstructor.sol for the contract itself.
     *
     * We use the bytes directly here, just because we don't have any good utilities in place for
     * generating these bytes ourselves.
     */
    private byte[] getFvmContractBytes() throws DecoderException {
        return Hex.decodeHex("60506040525b5b600a565b6088806100186000396000f300605060405260"
            + "00356c01000000000000000000000000900463ffffffff1680634a6a740714603b578063fc9ad4331"
            + "46043576035565b60006000fd5b60416056565b005b3415604e5760006000fd5b60546059565b005b"
            + "5b565b5b5600a165627a7a723058206bd6d88e9834838232f339ec7235f108a21441649a2cf876547"
            + "229e6c18c098c0029");
    }

    private byte[] getAvmContractBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(SimpleContract.class), new byte[0]).encodeToBytes();
    }

    private byte[] getNonPayableFunctionCallEncoding() throws DecoderException {
        return Hex.decodeHex("fc9ad433");   // calls the function named 'nonpayableFunction'
    }

    private byte[] getPayableFunctionCallEncoding() throws DecoderException {
        return Hex.decodeHex("4a6a7407");   // calls the function named 'payableFunction'
    }
}
