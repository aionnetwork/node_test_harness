package org.aion.harness.tests.integ.concurrent;

import org.aion.harness.tests.integ.AlternatingVmTest;
import org.aion.harness.tests.integ.AvmFailuresTest;
import org.aion.harness.tests.integ.AvmReceiptLogTest;
import org.aion.harness.tests.integ.AvmTxSmokeTest;
import org.aion.harness.tests.integ.BalanceTransferTest;
import org.aion.harness.tests.integ.BeaconHashTest;
import org.aion.harness.tests.integ.BulkBalanceTransferTest;
import org.aion.harness.tests.integ.CrossCallTest;
import org.aion.harness.tests.integ.FvmDataBillingTest;
import org.aion.harness.tests.integ.FvmTxSmokeTest;
import org.aion.harness.tests.integ.InternalTxTest;
import org.aion.harness.tests.integ.JavaApiSmokeTest;
import org.aion.harness.tests.integ.MetaTransactionTest;
import org.aion.harness.tests.integ.RemovedStorageTest;
import org.aion.harness.tests.integ.runner.ConcurrentRunner;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(ConcurrentRunner.class)
@Suite.SuiteClasses({
    BalanceTransferTest.class,
    CrossCallTest.class,
    BulkBalanceTransferTest.class,
    AvmTxSmokeTest.class,
    FvmTxSmokeTest.class,
    JavaApiSmokeTest.class
    , FvmDataBillingTest.class
    , AvmFailuresTest.class
    , AlternatingVmTest.class
    , RemovedStorageTest.class
    , AvmReceiptLogTest.class
    , InternalTxTest.class
    // TODO THESE TESTS IGNORED ONLY UNTIL EXTERNAL STAKER IS ADDED
    //, MetaTransactionTest.class
    //, BeaconHashTest.class
})
public class ConcurrentSuite {
    /**
     * This class should have no logic, nothing in it gets run. It is merely here as an entry point
     * for the concurrent runner, so that we can specify all of the tests that are to be included
     * in the concurrent test execution.
     */
}
