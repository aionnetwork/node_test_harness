package org.aion.harness.tests.integ;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import org.aion.harness.kernel.Address;;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.RPC;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.unity.Participant;
import org.aion.harness.main.unity.SealStakingBlockHelper;
import org.aion.harness.tests.integ.runner.ExcludeNodeType;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class SealStakingBlockTest {
    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.SealStakingBlockTest");

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    private final StakingContractHelper stakingContractHelper;

    PrivateKey stakerKey;

    public SealStakingBlockTest() throws InvalidKeySpecException {
        stakingContractHelper =
            new StakingContractHelper(preminedAccount, rpc, prepackagedLogEventsFactory, listener);

        stakerKey = PrivateKey.random();
    }

    @Test
    public void testSealStakingBlock() throws Exception {

        stakingContractDeployRegisterAndVote();

        log.log("call SealStakingBlockHelper");
        SealStakingBlockHelper call = new SealStakingBlockHelper(stakerKey , rpc);

        log.log("call sealBlock");
        assertTrue(call.sealBlock());

        //TODO: listen the staking block sealed.
    }

    private void stakingContractDeployRegisterAndVote() throws InterruptedException {
        log.log("Deploying avm contract...");

        TransactionReceipt receipt = stakingContractHelper.deployStakingContractWithDefaultOwner();
        assertTrue(receipt.transactionWasSuccessful());

        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        Participant participant = new Participant(stakerKey, avmContract, rpc);

        log.log("Funding this participant...");

        receipt = stakingContractHelper.fundParticipant(participant.getParticipantAddress());
        assertTrue(receipt.transactionWasSuccessful());

        log.log("Registering address " + participant.getParticipantAddress() + "...");

        receipt = stakingContractHelper.sendCall(participant.getRegisterTransaction());
        log.log(receipt);
        assertTrue(receipt.transactionWasSuccessful());

        BigInteger votedAmount = new BigInteger("1000000000");

        log.log("Participant 1 is voting " + votedAmount);

        receipt = stakingContractHelper.sendCall(participant.getVoteTransaction(participant.getParticipantAddress(), votedAmount));
        log.log(receipt);
        assertTrue(receipt.transactionWasSuccessful());
    }
}
