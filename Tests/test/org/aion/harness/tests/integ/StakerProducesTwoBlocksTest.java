package org.aion.harness.tests.integ;

import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
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
import org.aion.util.conversions.Hex;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.*;

/* NOTE: These tests can fail with some small probability depending on 
*       the unpredictable consequences of staking and mining blocks competing with each other.
*       
*       If they do fail once, but pass the next time, they're probably fine.
 */

@RunWith(SequentialRunner.class)
public class StakerProducesTwoBlocksTest {
    private static final long ENERGY_LIMIT_CREATE = 4_233_567L;
    private static final long ENERGY_LIMIT_CALL = 1_233_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(9_000_000_000_000_000_000L));
    
    private PrivateKey hardCodedAddress;
    private Address stakingContractAddress;
    
    @Rule
    private LocalNodeListener listener = new LocalNodeListener();
    
    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    public StakerProducesTwoBlocksTest() throws InvalidKeySpecException {
        hardCodedAddress = PrivateKey.fromBytes(Hex.decode("cc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4"));
        stakingContractAddress = new Address(Hex.decode("a056337bb14e818f3f53e13ab0d93b6539aa570cba91ce65c716058241989be9"));
    }
    
    @Test
    public void testExternalStakerProducesTwoBlocks() throws Exception {
        System.out.println("Funding the hard coded address...");
        System.out.println(hardCodedAddress.getAddress());

        TransactionReceipt receipt = fundAddress(hardCodedAddress.getAddress());
        assertTrue(receipt.transactionWasSuccessful());

        this.preminedAccount.incrementNonce();

        System.out.println("Deploying staking contract...");

        receipt = deployStakingContract();
        assertTrue(receipt.transactionWasSuccessful());

        Address deployedAddress = receipt.getAddressOfDeployedContract().get();
        assertNotNull(deployedAddress);
        assertEquals(stakingContractAddress, deployedAddress);

        Participant hardCodedStaker = new Participant(hardCodedAddress, stakingContractAddress, rpc);

        System.out.println("Registering address " + hardCodedAddress + "...");

        receipt = sendCall(hardCodedStaker.getRegisterTransaction());
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        System.out.println("Hard coded staker is voting for herself...");
        receipt = sendCall(hardCodedStaker.getVoteTransaction(hardCodedAddress.getAddress(), new BigInteger("1000000000")));
        System.out.println(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        // Successfully produce a block
        assertTrue(produceOneStakingBlock(hardCodedStaker));
        
        // Wait for the internal miner to produce at least one mined block
        long topBlock = rpc.blockNumber().getResult();
        while (topBlock == rpc.blockNumber().getResult()) {
            Thread.sleep(1000);
        }
        
        // Successfully produce a second block
        assertTrue(produceOneStakingBlock(hardCodedStaker));
    }
    
    private boolean produceOneStakingBlock(Participant staker) throws InterruptedException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] newSeed = staker.createAndSendStakingBlock();

        // we will repeatedly call getSeed for a minute, expecting the seed of the best staking block
        // to update to our submitted block
        int iterations = 10;

        while(!Arrays.equals(newSeed, rpc.getSeed()) && iterations > 0) {
            Thread.sleep(5000);
            iterations--;
        }

        // if we broke out of the loop because we timed out, the test is considered to have failed
        return iterations != 0;
    }

        private TransactionReceipt deployStakingContract() throws InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignAvmCreateTransaction(
                this.hardCodedAddress,
                rpc.getNonce(hardCodedAddress.getAddress()).getResult(),
                getStakingContractBytes(),
                ENERGY_LIMIT_CREATE,
                ENERGY_PRICE,
                BigInteger.ZERO);
        assertTrue(result.isSuccess());

        return sendCall(result.getTransaction());
    }

    private TransactionReceipt fundAddress(Address recipient) throws InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignGeneralTransaction(
                this.preminedAccount.getPrivateKey(),
                this.preminedAccount.getNonce(),
                recipient,
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
            listenResult = futureProcessed.get(2, TimeUnit.MINUTES);
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
