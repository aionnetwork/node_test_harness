package org.aion.harness.tests.integ;

import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.unity.Participant;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.tests.contracts.StakingRegistry;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.*;

@RunWith(SequentialRunner.class)
public class StakingRegistryTest {
    private static final long ENERGY_LIMIT_CREATE = 4_233_567L;
    private static final long ENERGY_LIMIT_CALL = 1_233_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(9_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();
    
    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();
    
    @Test
    public void testSingleParticipantCanRegister() throws Exception {

        System.out.println("Deploying avm contract...");
        
        TransactionReceipt receipt = deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());
        
        this.preminedAccount.incrementNonce();
        
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);
        
        Participant participant = new Participant(avmContract, rpc);

        System.out.println("Funding this participant...");
        
        receipt = fundParticipant(participant.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();
        
        System.out.println("Registering address " + participant.getParticipantAddress() + "...");
        
        receipt = sendCall(participant.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());
    }
    
    @Test
    public void testAnyoneCanVoteForRegisteredStaker() throws Exception {

        System.out.println("Deploying avm contract...");

        TransactionReceipt receipt = deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        Participant participant1 = new Participant(avmContract, rpc);

        System.out.println("Funding participant1...");

        receipt = fundParticipant(participant1.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        Participant participant2 = new Participant(avmContract, rpc);

        System.out.println("Funding participant2...");

        receipt = fundParticipant(participant2.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        System.out.println("Registering participant1 " + participant1.getParticipantAddress() + "...");

        receipt = sendCall(participant1.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        System.out.println("Participant 2 is voting for participant 1...");

        receipt = sendCall(participant2.getVoteTransaction(participant1.getParticipantAddress(), new BigInteger("20000")));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());
    }

    @Test
    public void testVoteAndUnvote() throws Exception {

        System.out.println("Deploying avm contract...");

        TransactionReceipt receipt = deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        Participant participant1 = new Participant(avmContract, rpc);

        System.out.println("Funding participant1...");

        receipt = fundParticipant(participant1.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        Participant participant2 = new Participant(avmContract, rpc);

        System.out.println("Funding participant2...");

        receipt = fundParticipant(participant2.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        System.out.println("Registering participant1 " + participant1.getParticipantAddress() + "...");

        receipt = sendCall(participant1.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());
        
        BigInteger votedAmount = new BigInteger("20000");

        System.out.println("Participant 2 is voting " + votedAmount + " for participant 1...");

        receipt = sendCall(participant2.getVoteTransaction(participant1.getParticipantAddress(), votedAmount));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        BigInteger unvotedAmount = new BigInteger("10000");

        System.out.println("Participant 2 is unvoting " + unvotedAmount + " for participant 1...");

        receipt = sendCall(participant2.getUnvoteTransaction(participant1.getParticipantAddress(), unvotedAmount.longValue()));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        assertEquals(votedAmount.subtract(unvotedAmount).longValue(), participant1.getVote());
        assertEquals(0, participant2.getVote());
    }

    private TransactionReceipt deployStakingContract() throws InterruptedException, TimeoutException {
        TransactionResult result = RawTransaction.buildAndSignAvmCreateTransaction(
                this.preminedAccount.getPrivateKey(),
                this.preminedAccount.getNonce(),
                getStakingContractBytes(),
                ENERGY_LIMIT_CREATE,
                ENERGY_PRICE,
                BigInteger.ZERO);
        assertTrue(result.isSuccess());

        return sendCall(result.getTransaction());
    }

    private TransactionReceipt fundParticipant(Address participant) throws InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignGeneralTransaction(
                this.preminedAccount.getPrivateKey(),
                this.preminedAccount.getNonce(),
                participant,
                new byte[0],
                ENERGY_LIMIT_CALL,
                ENERGY_PRICE,
                BigInteger.valueOf(100_000_000_000_000_000L));
        assertTrue(result.isSuccess());

        return sendCall(result.getTransaction());
    }

    private TransactionReceipt sendCall(RawTransaction transaction) throws InterruptedException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);
        FutureResult<LogEventResult> futureProcessed = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = null;
        try {
            listenResult = futureProcessed.get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        assertTrue(listenResult.eventWasObserved());

        // Verify it was sealed and not rejected.
        assertFalse(transactionIsRejected.hasBeenObserved());
        assertTrue(transactionIsSealed.hasBeenObserved());
        System.out.println("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);

        return receiptResult.getResult();
    }

    private byte[] getStakingContractBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(StakingRegistry.class,
            ABIDecoder.class, ABIEncoder.class, ABIException.class, AionMap.class), new byte[0]).encodeToBytes();
    }
}
