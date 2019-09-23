package org.aion.harness.tests.integ.saturation;

import java.math.BigInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.result.RpcResult;

public final class Saturator implements Callable<SaturationReport> {
    private final RPC rpc = RPC.newRpc("127.0.0.1", "8545");
    private final String name;
    private final CyclicBarrier barrier;
    private final PrivateKey senderKey;

    public Saturator(int threadID, CyclicBarrier barrier, PrivateKey senderKey) {
        this.name = "[Saturator-#" + threadID + "]";
        this.barrier = barrier;
        this.senderKey = senderKey;
        Thread.currentThread().setName(this.name);
    }

    @Override
    public SaturationReport call() throws Exception {
        // Wait for all the sender threads to be ready to send.
        this.barrier.await();

        SaturationReport report = null;

        // Send the transactions.
        System.out.println(this.name + " sending the " + SaturationTest.NUM_TRANSACTIONS + " transactions ...");
        Address destination = PrivateKey.random().getAddress();

        try {
            for (int i = 0; i < SaturationTest.NUM_TRANSACTIONS; i++) {
                BigInteger nonce = BigInteger.valueOf(i);

                SignedTransaction transaction = SignedTransaction.newGeneralTransaction(this.senderKey, nonce, destination, new byte[0], SaturationTest.ENERGY_LIMIT, SaturationTest.ENERGY_PRICE, SaturationTest.TRANSFER_AMOUNT,
                    null);
                this.rpc.sendSignedTransaction(transaction);
            }
        } catch (Exception e) {
            report = SaturationReport.unsuccessful(this.name, "Failed creating transactions due to: " + e.getMessage());
        }

        // We only want to do the next steps if nothing has gone wrong so far.
        if (report == null) {
            System.out.println(this.name + " all transactions sent!");

            BigInteger expectedBalance = BigInteger.valueOf(SaturationTest.NUM_TRANSACTIONS).multiply(SaturationTest.TRANSFER_AMOUNT);

            // Verify that all the transactions were sent.
            // We just wait until the destination address has the expected balance or until we time out.
            System.out.println(this.name + " waiting for all the transactions to process ...");

            long startTimeInNanos = System.nanoTime();
            long currentTimeInNanos = startTimeInNanos;
            while ((report == null) && (currentTimeInNanos < (startTimeInNanos + SaturationTest.THREAD_TIMEOUT_IN_NANOS))) {

                RpcResult<BigInteger> balanceResult = this.rpc.getBalance(destination);
                if (!balanceResult.isSuccess()) {
                    report = SaturationReport.unsuccessful(this.name, "Failed to get destination balance due to: " + balanceResult.getError());
                }

                if (balanceResult.getResult().equals(expectedBalance)) {
                    report = SaturationReport.successful(this.name);
                }

                Thread.sleep(SaturationTest.THREAD_DELAY_IN_MILLIS);
                currentTimeInNanos = System.nanoTime();
            }

            // If the report is null then we must have timed out.
            if (report == null) {
                report = SaturationReport.unsuccessful(this.name, "Timed out waiting for transactions to process!");
            }

            System.out.println(this.name + " all transactions have been processed!");
        }

        return report;
    }
}
