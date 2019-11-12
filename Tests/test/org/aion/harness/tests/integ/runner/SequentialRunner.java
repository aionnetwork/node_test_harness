package org.aion.harness.tests.integ.runner;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.equihash.EquihashMiner;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.main.event.RustPrepackagedLogEvents;
import org.aion.harness.tests.integ.runner.exception.TestRunnerInitializationException;
import org.aion.harness.tests.integ.runner.exception.UnsupportedAnnotation;
import org.aion.harness.tests.integ.runner.internal.PreminedAccountFunder;
import org.aion.harness.tests.integ.runner.internal.StakingBlockSigner;
import org.aion.harness.tests.integ.runner.internal.TestAndResultQueueManager;
import org.aion.harness.tests.integ.runner.internal.TestContext;
import org.aion.harness.tests.integ.runner.internal.TestExecutor;
import org.aion.harness.tests.integ.runner.internal.TestNodeManager;
import org.aion.harness.tests.integ.runner.internal.TestResult;
import org.apache.commons.io.FileUtils;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

/**
 * Sequential Runner.  Responsible for starting up a node and executing tests against it.
 *
 * To run tests, node type must be specified using JVM system property <code>testNodes</code>.
 * Valid values:
 *
 *  <code>java,rust</code> - run with java and rust kernels
 *  <code>java</code> - run with java kernel only
 *  <code>rust</code> - run with rust kernel only
 *
 *  Multiple values may be specified, delimited with comma
 *
 * Gradle example: <code>./gradlew Tests:test -PtestNodes=java,rust</code>
 * If running in IDE, add <code>-PtestNodes=java</code> to JUnit VM arguments.
 *
 * Classes can override this behaviour using {@link ExcludeNodeType}.
 */
public final class SequentialRunner extends Runner {
    private final RunnerHelper helper;
    private final Class<?> testClass;
    private final Description testClassDescription;
    private final Map<NodeType, Description> nodeType2Description;

    private final EquihashMiner miner = EquihashMiner.defaultMiner();
    private StakingBlockSigner stakingBlockSigner = StakingBlockSigner.defaultStakingBlockSigner();

    public SequentialRunner(Class<?> testClass) {
        this.helper = new RunnerHelper();
        this.testClass = testClass;
        nodeType2Description = new LinkedHashMap<>(); // because run method depends on the order

        List<NodeType> nodesToTest = new LinkedList<>(helper.determineNodeTypes());

        this.testClassDescription = deriveTestDescription(testClass, nodeType2Description, nodesToTest);
    }

    @Override
    public Description getDescription() {
        return this.testClassDescription;
    }

    public void run(RunNotifier runNotifier) {
        // This is the method JUnit invokes to run all of the tests in our test class.

        // Verify the test class itself is only using annotations this runner knows how to handle.
        verifyTestClassAnnotations();
        verifyMethodAndFieldAnnotations();

        if (isTestClassIgnored()) {
            // This class is marked Ignore so we mark all tests ignored and finish up right here.
            for (Description nodeDescription : this.testClassDescription.getChildren()) {
                for (Description testDescription : nodeDescription.getChildren()) {
                    runNotifier.fireTestIgnored(testDescription);
                }
            }

            return;
        }

        PrintStream originalStdout = helper.replaceStdoutWithThreadSpecificOutputStream();
        PrintStream originalStderr = helper.replaceStderrWithThreadSpecificErrorStream();

        for(NodeType nt : nodeType2Description.keySet()) {

            miner.startMining();

            TestNodeManager testNodeManager = new TestNodeManager(nt);
            Description nodeDescription = nodeType2Description.get(nt);

            try {

                boolean beforeClassFailed = false;

                // If the class has a @BeforeClass method then run it now.
                try {
                    runBeforeClassMethodsIfAnyExists();
                } catch (Throwable e) {
                    // The @BeforeClass failed so mark every test as failed.
                    beforeClassFailed = true;
                    for (Description description : nodeDescription.getChildren()) {
                        runNotifier.fireTestStarted(description);
                        runNotifier.fireTestFailure(new Failure(description, e));
                        runNotifier.fireTestFinished(description);
                    }
                }

                // Run all of the test methods now. No exceptions will be thrown here.
                if (!beforeClassFailed) {
                    initializeAndStartNode(testNodeManager);
                    // StakingBlockSigner currently needs to be started after the node's rpc is up and running
                    stakingBlockSigner.start();
                    runTests(runNotifier, nodeDescription.getChildren(), nt, testNodeManager);
                }

                // If the class has a @AfterClass method then run it now.
                try {
                    if (!beforeClassFailed) {
                        runAfterClassMethodsIfAnyExists();
                    }
                } catch (Throwable e) {
                    // Document the afterClass error as if the test class itself failed, so we can show the error.
                    runNotifier.fireTestStarted(nodeDescription);
                    runNotifier.fireTestFailure(new Failure(nodeDescription, e));
                    runNotifier.fireTestFinished(nodeDescription);
                }

            } catch (Throwable e) {
                // We don't expect to see any exceptions here! But this is just in case and allows us to shut down the node in a finally.
                e.printStackTrace();
                throw e;
            } finally {
                miner.stopMining();
                stakingBlockSigner.stop();
                // Ensure that the node gets shut down properly.
                stopNode(testNodeManager);

                // Delete the log files unless specified not to.
                if (System.getProperty("skipCleanLogs") == null) {
                    try {
                        FileUtils
                            .deleteDirectory(new File(System.getProperty("user.dir") + "/logs"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Restore the original stdout and stderr back to System.
        System.setOut(originalStdout);
        System.setErr(originalStderr);
    }

    private void runTests(RunNotifier runNotifier, List<Description> tests, NodeType nt, TestNodeManager testNodeManager) {
        List<TestContext> testContexts = new ArrayList<>();
        for (Description testDescription : tests) {
                testContexts.add(new TestContext(this.testClass, testDescription));
        }

        TestAndResultQueueManager queueManager = new TestAndResultQueueManager(1);

        // Run all of the tests on a single thread.
        final PreminedAccountFunder paf;
        if(nt == NodeType.RUST_NODE) {
            paf = new PreminedAccountFunder(testNodeManager, new RustPrepackagedLogEvents());
        } else if (nt == NodeType.JAVA_NODE || nt == NodeType.PROXY_JAVA_NODE) {
            paf = new PreminedAccountFunder(testNodeManager, new JavaPrepackagedLogEvents());
        } else {
            throw new IllegalArgumentException(String.format(
                "Don't know how to construct PremindedAccountFunder for NodeType '%s'.",
                nt));
        }

        TestExecutor testExecutor = new TestExecutor(testNodeManager, paf, queueManager, nt);
        Thread executorThread = new Thread(testExecutor);
        executorThread.start();

        // Dynamically dispatch all of the tests to the thread.
        for (TestContext testContext : testContexts) {
            queueManager.putTest(testContext);
        }

        // Close the queue to notify threads that all tests have been submitted.
        queueManager.reportAllTestsSubmitted();

        // Collect the results of the tests and notify JUnit.
        TestResult result = queueManager.takeResult();
        while (result != null) {
            if (result.ignored) {
                runNotifier.fireTestIgnored(result.description);
            } else {
                runNotifier.fireTestStarted(result.description);
                if (!result.success) {
                    runNotifier.fireTestFailure(new Failure(result.description, result.error));
                }

                // Print the stdout and stderr of the current test to console.
                System.out.write(result.stdout, 0, result.stdout.length);
                System.err.write(result.stderr, 0, result.stderr.length);
                System.out.flush();
                System.err.flush();

                runNotifier.fireTestFinished(result.description);
            }
            result = queueManager.takeResult();
        }

        // Wait for the thread to exit.
        try {
            executorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTestClassIgnored() {
        for (Annotation annotation : this.testClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.equals(org.junit.Ignore.class)) {
                return true;
            }
        }
        return false;
    }

    private void initializeAndStartNode(TestNodeManager testNodeManager) {
        try {
            testNodeManager.startLocalNode();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new TestRunnerInitializationException("Error setting up the local node", e);
        }
    }

    private void runAfterClassMethodsIfAnyExists() {
        List<Method> afterClassMethods = getAfterClassMethods();

        for (Method afterClassMethod : afterClassMethods) {
            verifyAfterClassMethodAnnotations(afterClassMethod);
            try {
                afterClassMethod.invoke(null);
            } catch (Throwable e) {
                throw new TestRunnerInitializationException("Error encountered in the @AfterClass method: " + afterClassMethod.getName(), e);
            }
        }
    }

    private void runBeforeClassMethodsIfAnyExists() {
        List<Method> beforeClassMethods = getBeforeClassMethods();

        for (Method beforeClassMethod : beforeClassMethods) {
            verifyBeforeClassMethodAnnotations(beforeClassMethod);
            try {
                beforeClassMethod.invoke(null);
            } catch (Throwable e) {
                throw new TestRunnerInitializationException("Error encountered in the @BeforeClass method: " + beforeClassMethod.getName(), e);
            }
        }
    }

    private void stopNode(TestNodeManager testNodeManager) {
        try {
            testNodeManager.shutdownLocalNode();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new TestRunnerInitializationException("Error shutting down the local node", e);
        }
    }

    /**
     * This is the description of all the test methods in the test class. JUnit uses the description
     * to display info in your IDE etc. about which test is which.
     *
     * The returned Description will correspond to the test class.  Each node type to test will
     * be represented by a child description.  Each of these child descriptions, will have children
     * descripions; one for each test method.
     *
     * Visual example:
     * <code>
     *     Class description
     *     |--Rust description
     *     ||----testMethod1() description
     *     ||----testMethod2() description
     *     ||----testMethodN() description
     *     |--Java description
     *     ||----testMethod1() description
     *     ||----testMethod2() description
     *     ||----testMethodN() description
     * </code>
     * @param testClass the class of the JUnit test
     * @param nodeType2Description method will add pairs (NodeType, Description) for each NodeType in
     *                             the nodesToTest parameter, where the Description is a child
     *                             of the Description of the test class.
     * @param nodesToTest the node types that should be used to execute this test class
     * @return Description for test class
     */
    private Description deriveTestDescription(Class<?> testClass,
                                              Map<NodeType, Description> nodeType2Description,
                                              List<NodeType> nodesToTest) {
        Description description = Description.createTestDescription(testClass, testClass.getName());

        for(NodeType nt : nodesToTest) {
            Description nd = Description.createSuiteDescription(nt.name());

            for (Method testMethod : testClass.getMethods()) {
                if (testMethod.isAnnotationPresent(org.junit.Test.class)) {
                    nd.addChild(Description.createTestDescription(testClass, nt.name() + ":" + testMethod.getName()));
                }
            }

            description.addChild(nd);
            nodeType2Description.put(nt, nd);
        }

        return description;
    }

    private List<Method> getAfterClassMethods() {
        List<Method> afterClassMethods = new ArrayList<>();
        for (Method method : this.testClass.getMethods()) {
            if (method.isAnnotationPresent(org.junit.AfterClass.class)) {
                afterClassMethods.add(method);
            }
        }
        return afterClassMethods;
    }

    private List<Method> getBeforeClassMethods() {
        List<Method> beforeClassMethods = new ArrayList<>();
        for (Method method : this.testClass.getMethods()) {
            if (method.isAnnotationPresent(org.junit.BeforeClass.class)) {
                beforeClassMethods.add(method);
            }
        }
        return beforeClassMethods;
    }

    private void verifyBeforeClassMethodAnnotations(Method beforeClassMethod) {
        for (Annotation annotation : beforeClassMethod.getAnnotations()) {
            if (!annotation.annotationType().equals(org.junit.BeforeClass.class)) {
                throw new UnsupportedAnnotation("This custom runner does not allow a @BeforeClass method to have any other annotations.");
            }
        }
    }

    private void verifyAfterClassMethodAnnotations(Method afterClassMethod) {
        for (Annotation annotation : afterClassMethod.getAnnotations()) {
            if (!annotation.annotationType().equals(org.junit.AfterClass.class)) {
                throw new UnsupportedAnnotation("This custom runner does not allow a @AfterClass method to have any other annotations.");
            }
        }
    }

    private void verifyTestClassAnnotations() {
        for (Annotation annotation : this.testClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!annotationType.equals(org.junit.Ignore.class)
                && !annotationType.equals(org.junit.runner.RunWith.class)
                && !annotationType.equals(ExcludeNodeType.class)
            ) {
                throw new UnsupportedAnnotation("This custom runner does not support the annotation: " + annotation);
            }
        }
    }

    private void verifyMethodAndFieldAnnotations() {
        for (Method method : this.testClass.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (!annotationType.equals(org.junit.Test.class) && !annotationType.equals(org.junit.Before.class)
                    && !annotationType.equals(org.junit.After.class) && !annotationType.equals(org.junit.BeforeClass.class)
                    && !annotationType.equals(org.junit.AfterClass.class) && !annotationType.equals(org.junit.Ignore.class)) {
                    throw new UnsupportedAnnotation("This custom runner only supports the following method annotations:"
                        + " Ignore, Test, Before, After, BeforeClass, AfterClass. Found: " + annotation);
                }
            }
        }
        for (Field field : this.testClass.getDeclaredFields()) {
            for (Annotation annotation : field.getAnnotations()) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (!annotationType.equals(org.junit.Rule.class)) {
                    throw new UnsupportedAnnotation("This custom runner only supports the following field annotations: Rule. Found: " + annotation);
                }
            }
        }
    }
}
