package org.aion.harness.tests.integ;

import java.security.spec.InvalidKeySpecException;
import org.aion.harness.kernel.Address;
import org.aion.harness.main.RPC;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.unity.Participant;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import static org.junit.Assert.*;

@RunWith(SequentialRunner.class)
public class StakingRegistryTest {


    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(9_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();
    
    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    private StakingContractHelper stakingContractHelper =
            new StakingContractHelper(preminedAccount, rpc, prepackagedLogEventsFactory, listener);

    public StakingRegistryTest() throws InvalidKeySpecException {
    }

    @Test
    public void testSingleParticipantCanRegister() throws Exception {

        System.out.println("Deploying avm contract...");
        
        TransactionReceipt receipt = stakingContractHelper.deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());
        
        this.preminedAccount.incrementNonce();

        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);
        
        Participant participant = new Participant(avmContract, rpc);

        System.out.println("Funding this participant...");
        
        receipt = stakingContractHelper.fundParticipant(participant.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();
        
        System.out.println("Registering address " + participant.getParticipantAddress() + "...");
        
        receipt = stakingContractHelper.sendCall(participant.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

    }
    
    @Test
    public void testAnyoneCanVoteForRegisteredStaker() throws Exception {

        System.out.println("Deploying avm contract...");

        TransactionReceipt receipt = stakingContractHelper.deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        Participant participant1 = new Participant(avmContract, rpc);

        System.out.println("Funding participant1...");

        receipt = stakingContractHelper.fundParticipant(participant1.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        Participant participant2 = new Participant(avmContract, rpc);

        System.out.println("Funding participant2...");

        receipt = stakingContractHelper.fundParticipant(participant2.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        System.out.println("Registering participant1 " + participant1.getParticipantAddress() + "...");

        receipt = stakingContractHelper.sendCall(participant1.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        System.out.println("Participant 2 is voting for participant 1...");

        receipt = stakingContractHelper.sendCall(participant2.getVoteTransaction(participant1.getParticipantAddress(), new BigInteger("20000")));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());
    }

    @Test
    public void testVoteAndUnvote() throws Exception {

        System.out.println("Deploying avm contract...");

        TransactionReceipt receipt = stakingContractHelper.deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        Participant participant1 = new Participant(avmContract, rpc);

        System.out.println("Funding participant1...");

        receipt = stakingContractHelper.fundParticipant(participant1.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        Participant participant2 = new Participant(avmContract, rpc);

        System.out.println("Funding participant2...");

        receipt = stakingContractHelper.fundParticipant(participant2.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        System.out.println("Registering participant1 " + participant1.getParticipantAddress() + "...");

        receipt = stakingContractHelper.sendCall(participant1.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());
        
        BigInteger votedAmount = new BigInteger("20000");

        System.out.println("Participant 2 is voting " + votedAmount + " for participant 1...");

        receipt = stakingContractHelper.sendCall(participant2.getVoteTransaction(participant1.getParticipantAddress(), votedAmount));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        BigInteger unvotedAmount = new BigInteger("10000");

        System.out.println("Participant 2 is unvoting " + unvotedAmount + " for participant 1...");

        receipt = stakingContractHelper.sendCall(participant2.getUnvoteTransaction(participant1.getParticipantAddress(), unvotedAmount.longValue()));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        assertEquals(votedAmount.subtract(unvotedAmount).longValue(), participant1.getVote());
        assertEquals(0, participant2.getVote());
    }


}
