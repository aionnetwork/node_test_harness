package org.aion.harness.tests.integ.saturation;

import java.math.BigInteger;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.result.RpcResult;
import org.junit.Assert;

public final class NodeSender implements Runnable {
    public final String name;
    private final CyclicBarrier barrier;
    private final RPC rpc;
    private final PrivateKey senderKey;
    private final Address senderAddress;

    public volatile Exception error;

    public NodeSender(int threadID, CyclicBarrier barrier, String IP, PrivateKey sender) {
        Assert.assertNotNull(barrier);
        Assert.assertNotNull(IP);
        Assert.assertNotNull(sender);
        this.name = "[Saturator-#" + threadID + "]";
        this.barrier = barrier;
        this.rpc = RPC.newRpc(IP, "8545");
        this.senderKey = sender;
        this.senderAddress = this.senderKey.getAddress();
        Thread.currentThread().setName(this.name);
    }

    @Override
    public void run() {
        try {
            // Wait for all the sender threads to be ready to send.
            this.barrier.await();

            Address destination = PrivateKey.random().getAddress();

            // Send the transactions at the specified rate.
            System.out.println(this.name + " sending " + SaturationTest.NUM_TRANSACTIONS + " transactions ...");
            long absoluteStart = System.nanoTime();
            int nonce = 0;
            while (nonce < SaturationTest.NUM_TRANSACTIONS) {
                long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                int count = 0;

                // Send until we hit the max.
                while (count < SaturationTest.TRANSACTIONS_PER_SECOND) {
                    SignedTransaction transaction = SignedTransaction.newGeneralTransaction(this.senderKey, BigInteger.valueOf(nonce), destination, new byte[0], SaturationTest.ENERGY_LIMIT, SaturationTest.ENERGY_PRICE, SaturationTest.TRANSFER_AMOUNT);
                    RpcResult<ReceiptHash> sendResult = this.rpc.sendSignedTransaction(transaction);
                    if (!sendResult.isSuccess()) {
                        throw new IllegalStateException("Failed to send transaction over RPC due to: " + sendResult);
                    }
                    count++;
                    nonce++;
                }

                // Sleep for the amount of time remaining, if any time is remaining.
                long timeLeft = endTime - System.nanoTime();
                if (timeLeft > 0) {
                    long timeToSleepMillis = TimeUnit.NANOSECONDS.toMillis(timeLeft);
                    if (timeToSleepMillis > 0) {
                        Thread.sleep(timeToSleepMillis);
                    }
                }
            }
            long absoluteEnd = System.nanoTime();
            System.out.println(this.name + " finished sending all transactions! (took apx. " + TimeUnit.NANOSECONDS.toSeconds(absoluteEnd - absoluteStart) + " seconds)");

        } catch (Exception e) {
            this.error = e;
            return;
        }
    }
}
