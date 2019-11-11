package org.aion.equihash;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.aion.harness.main.RPC;
import org.aion.harness.main.types.BlockTemplate;
import org.aion.harness.main.types.MinedBlockSolution;
import org.aion.harness.util.SimpleLog;

public class EquihashMiner {

    private final RPC rpc;

    // Be careful, trying to log anything in a different thread will probably cause a crash.
    private final SimpleLog logger = new SimpleLog("EquihashMiner");

    private int cpuThreads = 1;

    private static final int N = 210;

    private static final int K = 9;

    private boolean isMining;

    private volatile BlockTemplate blockTemplate;

    // Equihash solver implementation
    private final Equihash miner = new Equihash(N, K);

    private static final int BLOCK_TEMPLATE_INTERVAL = 1000; //ms

    // Status scheduler
    private ScheduledThreadPoolExecutor scheduledWorkers;

    /** Miner threads */
    private final List<Thread> threads = new ArrayList<>();

    public EquihashMiner(String ip, String port) {
        rpc = RPC.newRpc(ip, port);
    }

    public static EquihashMiner defaultMiner() {
        return new EquihashMiner("127.0.0.1", "8545");
    }

    public void startMining() {
        if (isMining) { return; }

        logger.log("Starting Mining");

        isMining = true;

        scheduledWorkers = new ScheduledThreadPoolExecutor(1);

        scheduledWorkers.scheduleWithFixedDelay(
                new GetBlockTemplateTask(),
                0,
                BLOCK_TEMPLATE_INTERVAL,
                TimeUnit.MILLISECONDS);

        for (int i = 0; i < cpuThreads; i++) {
            Thread t = new Thread(this::mine, "miner-" + (i + 1));

            t.start();
            threads.add(t);
        }
    }

    public void stopMining() {
        if (!isMining) { return; }

        logger.log("Stopping Mining");

        isMining = false;

        scheduledWorkers.shutdownNow();

        // interrupt
        for (Thread t : threads) {
            t.interrupt();
        }

        // join
        for (Thread t : threads) {
            try {
                t.join();
            }
            catch (Exception ignored) {}
        }
        threads.clear();
    }

    /** Keeps mining until the thread is interrupted */
    private void mine() {
        byte[] nonce;
        while (!Thread.currentThread().isInterrupted()) {
            if (blockTemplate == null) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            } else {
                BlockTemplate block = blockTemplate.copy();
                
                // A new array must be created each loop
                // If reference is reused the array contents may be changed
                // before block sealed causing validation to fail
                nonce = new byte[32];
                ThreadLocalRandom.current().nextBytes(nonce);

                MinedBlockSolution solution = miner.mine(block, nonce);

                if (solution != null) {
                    try {
                        rpc.submitSolution(solution);
                    }
                    catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }

    public boolean isMining() {
        return isMining;
    }

    private class GetBlockTemplateTask implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("get_block_template");

            try {
                blockTemplate = rpc.getBlockTemplate();
            }
            catch (Exception ignored) {}
        }
    }
}
