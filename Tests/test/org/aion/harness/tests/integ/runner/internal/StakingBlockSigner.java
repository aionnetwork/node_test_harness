package org.aion.harness.tests.integ.runner.internal;

import java.io.File;
import java.io.IOException;

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
        return new StakingBlockSigner(defaultPrivateKey, defaultCoinbaseAddress, defaultIp, defaultPort);
    }

    public void start() {
        if (process != null) { return; }

        try {
            ProcessBuilder builder = new ProcessBuilder("java", "-cp", "block_signer.jar:lib/*", "org.aion.staker.BlockSigner", signingAddressPrivateKey, coinbaseAddress, ip, port)
                .directory(new File(EXTERNAL_STAKER_PATH))
                .redirectOutput(new File("/dev/null"));
            process = builder.start();
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
