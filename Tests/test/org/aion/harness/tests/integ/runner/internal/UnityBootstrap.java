package org.aion.harness.tests.integ.runner.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class UnityBootstrap {
    private static final String BOOTSTRAP_PATH = System.getProperty("user.dir") + "/../tooling/customBootstrap";

    public static void bootstrap() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("./bootstrap.sh").directory(new File(BOOTSTRAP_PATH));
        Process bootstrapper = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(bootstrapper.getInputStream()));
        String line;
        while((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        if (bootstrapper.waitFor() != 0 ) {
            throw new RuntimeException("Bootstrap script did not complete properly!");
        }
    }
}
