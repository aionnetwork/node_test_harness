package org.aion.harness.tests.integ.runner.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;

public class StakingBlockSigner {
    public static final String defaultCoinbaseAddress = "0xa02df9004be3c4a20aeb50c459212412b1d0a58da3e1ac70ba74dde6b4accf4b";
    public static final String defaultPrivateKey = "0xcc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4";
    public static final String defaultIp = "127.0.0.1";
    public static final String defaultPort = "8545";
    private final String EXTERNAL_STAKER_PATH = System.getProperty("user.dir") + "/../tooling/externalStaker";
    private final String coinbaseAddress;
    private final String signingAddressPrivateKey;
    private final String ip;
    private final String port;
    private Process process;

    public StakingBlockSigner(String signingAddressPrivateKey, String coinbaseAddress, String ip, String port) {
        this.signingAddressPrivateKey = signingAddressPrivateKey;
        this.coinbaseAddress = coinbaseAddress;
        this.ip = ip;
        this.port = port;
    }

    public static StakingBlockSigner defaultStakingBlockSigner() {
        return new StakingBlockSigner(defaultPrivateKey, defaultCoinbaseAddress, defaultIp, System.getProperty("rpcPort"));
    }

    public void start() {
        if (process != null) { return; }

        try {
            // "amity" is a hack to make this work. The Block signer doesn't have a "custom" option,
            // but the address used to deploy the staking contract on amity is the same one we use here.
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", "block_signer-1.2.jar", signingAddressPrivateKey, coinbaseAddress, "amity", ip, port)
                .directory(new File(EXTERNAL_STAKER_PATH))
                .redirectOutput(Redirect.DISCARD)
            ;
            process = builder.start();
            
            // Stall for a moment to make sure this started up (note that this is unreliable but we only expect this failure in response to environmental issues - missing/incorrect JVM, etc)
            // (in the future, we could change this to wait out STDOUT being produced in order to consider the tool bootstrapped, etc, if we need this to be reliable).
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new AssertionError("Interruption not used", e);
            }
            if (!this.process.isAlive()) {
                // Read any error this process logged before termination.
                StringBuilder stringBuilder = new StringBuilder("Failed to start block_signer.jar:\n");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.process.getErrorStream()))) {
                    String line = null;
                    while (null != (line = reader.readLine())) {
                        stringBuilder.append(line);
                        stringBuilder.append("\n");
                    }
                }
                this.process = null;
                throw new RuntimeException(stringBuilder.toString());
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Could not start Staking Block Signer");
        }
    }

    public void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}
