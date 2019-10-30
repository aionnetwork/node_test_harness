package org.aion.harness.tests.integ.runner.internal;

import java.io.File;
import java.io.IOException;

public class StakingBlockSigner {
    private static final String EXTERNAL_STAKER_PATH = System.getProperty("user.dir") + "/../tooling/externalStaker";

    public static Process startExternalStaker() {
        try {
            ProcessBuilder builder = new ProcessBuilder("./launchStaker.sh").directory(new File(EXTERNAL_STAKER_PATH));
            return builder.start();
        }
        catch (IOException e) {
            throw new RuntimeException("Could not start Staking Block Signer");
        }
    }
}
