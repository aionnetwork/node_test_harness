package org.aion.harness.tests.integ.runner;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.equihash.EquihashMiner;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.main.event.RustPrepackagedLogEvents;
import org.aion.harness.tests.integ.runner.exception.TestRunnerInitializationException;
import org.aion.harness.tests.integ.runner.exception.UnexpectedTestRunnerException;
import org.aion.harness.tests.integ.runner.exception.UnsupportedAnnotation;
import org.aion.harness.tests.integ.runner.internal.FailedClass;
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
import org.junit.runners.Suite.SuiteClasses;

/**
 * Concurrently Runner.  Responsible for starting up a node and executing tests against it.
 * Test case execution will be parallelized, but each node will be tested one at a time.
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
public final class ConcurrentRunner extends Runner {
    // Maximum number of threads to be used to run the tests. Our max is high because our tests are IO-bound.
    private static final int MAX_NUM_THREADS = 50;

    private final EquihashMiner miner = EquihashMiner.defaultMiner();
    private StakingBlockSigner stakingBlockSigner = StakingBlockSigner.defaultStakingBlockSigner();

    private final RunnerHelper helper;

    private final Class<?> suiteClass;
    private final Description testSuiteDescription;
    private final Map<NodeType, Map<Class<?>, Description>> node2ClassDescriptions;

    public ConcurrentRunner(Class<?> suiteClass) {
        this.helper = new RunnerHelper();
        this.suiteClass = suiteClass;

        this.testSuiteDescription = Description.createSuiteDescription(suiteClass);

        this.node2ClassDescriptions = new LinkedHashMap<>();
        List<NodeType> nodesToTest = new LinkedList<>(helper.determineNodeTypes());

        for(NodeType nt: nodesToTest) {
            Map<Class<?>, Description> class2Desc = new HashMap<>();
            Description nodeDesc = createSuiteDescriptionForNode(nt, suiteClass, class2Desc);
            node2ClassDescriptions.put(nt, class2Desc);
            testSuiteDescription.addChild(nodeDesc);
        }
    }

    @Override
    public Description getDescription() {
        return this.testSuiteDescription;
    }

    @Override
    public void run(RunNotifier runNotifier) {
        // This is the method JUnit invokes to run all of the tests in our test class.

        // Verify the test classes themselves are only using annotations this runner knows how to handle.
        verifyAnnotationsOfTestClasses();
        verifyAnnotationsOfAllMethodsAndFields();

        if (isTestSuiteIgnored()) {
            // Ignore every test in the whole suite.
            for (Description testClassDescription : this.testSuiteDescription.getChildren()) {
                for (Description testDescription : testClassDescription.getChildren()) {
                    runNotifier.fireTestIgnored(testDescription);
                }
            }

            return;
        }

        PrintStream originalStdout = helper.replaceStdoutWithThreadSpecificOutputStream();
        PrintStream originalStderr = helper.replaceStderrWithThreadSpecificErrorStream();

        for (NodeType nt : node2ClassDescriptions.keySet()) {

            miner.startMining();
            
            TestNodeManager testNodeManager = new TestNodeManager(nt);

            // Start up the local node before any tests are run.
            initializeAndStartNode(testNodeManager);

            try {
                // StakingBlockSigner currently needs to be started after the node's rpc is up and running
                stakingBlockSigner.start();

                // Run every @BeforeClass method in any of the test classes.
                List<FailedClass> failedClasses = runAllBeforeClassMethodsAndReturnFailedClasses(nt);

                // If any @BeforeClass methods failed, then every test in that class must be marked failed.
                notifyRunnerAllTestsInAllFailedClassesAreFailed(failedClasses, runNotifier, nt);

                // Grab all of the test classes whose @BeforeClass methods did not fail, we will run these now.
                List<Class<?>> allNonFailedTests = getAllNonFailedTestClassesRemaining(
                    failedClasses, nt);

                // Run all of the test methods now. No exceptions will be thrown here.
                runSuiteTests(allNonFailedTests, runNotifier, nt, testNodeManager);

                // Run any @AfterClass method in any of the test classes.
                failedClasses = runAllAfterClassMethodsAndReturnFailedClasses(allNonFailedTests);

                // If any @AfterClass methods failed, then we mark the test class itself as failed in JUnit to display this information.
                notifyRunnerAfterClassMethodsFailed(failedClasses, runNotifier, nt);

            } catch (Throwable e) {
                // We do not expect any exceptions to ever make it here. This is just to ensure our node
                // gets stopped in the finally block.
                e.printStackTrace();
                throw new UnexpectedTestRunnerException("Unexpected throwable!", e);
            } finally {
                miner.stopMining();
                stakingBlockSigner.stop();

                // Ensure that the node gets shut down properly.
                stopNode(testNodeManager);

                // Delete the log files unless specified not to.
                if (System.getProperty("skipCleanLogs") == null) {
                    try {
                        FileUtils.deleteDirectory(new File(System.getProperty("user.dir") + "/logs"));
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

    /**
     * Grabs all of the N test methods from the given test classes and hands them out to the threads.
     * The minimum of (N, MAX_NUM_THREADS) threads will be used to run the tests.
     *
     * This method will wait on the results of all the tests and notify JUnit of their progress.
     *
     * When this method returns all tests will be done being processed.
     */
    private void runSuiteTests(List<Class<?>> testClasses, RunNotifier runNotifier, NodeType nt, TestNodeManager testNodeManager) {
        if(testClasses.isEmpty()) {
            // if there's nothing to test, no point in going through the exercise of
            // enqueuing/dequeuing TestContexts and TestResults
            return;
        }

        List<TestContext> allTestContexts = getTestContextsForAllTests(testClasses, nt);
        int numThreads = Math.min(MAX_NUM_THREADS, allTestContexts.size());
        TestAndResultQueueManager queueManager = new TestAndResultQueueManager(numThreads);

        final PreminedAccountFunder paf;
        if(nt == NodeType.RUST_NODE) {
            paf = new PreminedAccountFunder(testNodeManager, new RustPrepackagedLogEvents());
        } else if(nt == NodeType.JAVA_NODE || nt == NodeType.PROXY_JAVA_NODE) {
            paf = new PreminedAccountFunder(testNodeManager, new JavaPrepackagedLogEvents());
        } else {
            throw new IllegalArgumentException(
                "Don't know how to construct PreminedAccountFunder for node type" + nt.name());
        }

        List<TestExecutor> testExecutors = createTestExecutors(numThreads, paf, queueManager, testNodeManager, nt);
        List<Thread> executorThreads = createExecutorThreads(testExecutors);

        // Start the threads.
        for (Thread executorThread : executorThreads) {
            executorThread.start();
        }

        // Dynamically dispatch all of the tests to the threads.
        for (TestContext testContext : allTestContexts) {
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

        // Wait for all the threads to exit.
        for (Thread executorThread : executorThreads) {
            try {
                executorThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<TestExecutor> createTestExecutors(int num,
                                                   PreminedAccountFunder paf,
                                                   TestAndResultQueueManager queueManager,
                                                   TestNodeManager testNodeManager,
                                                   NodeType nt) {
        List<TestExecutor> threads = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            threads.add(new TestExecutor(testNodeManager,
                paf,
                queueManager,
                nt)
            );
        }
        return threads;
    }

    private List<Thread> createExecutorThreads(List<TestExecutor> testExecutors) {
        List<Thread> threads = new ArrayList<>();
        for (TestExecutor testExecutor : testExecutors) {
            threads.add(new Thread(testExecutor));
        }
        return threads;
    }

    private List<TestContext> getTestContextsForAllTests(List<Class<?>> testClasses, NodeType nt) {
        List<TestContext> contexts = new ArrayList<>();
        for (Class<?> testClass : testClasses) {
            Description testClassDescription = this.node2ClassDescriptions.get(nt).get(testClass);

            for (Description testDescription : testClassDescription.getChildren()) {
                contexts.add(new TestContext(testClass, testDescription));
            }
        }
        return contexts;
    }

    private List<Class<?>> getAllNonFailedTestClassesRemaining(List<FailedClass> failedClasses, NodeType nt) {
        List<Class<?>> nonFailedTestClass = new ArrayList<>();
        nonFailedTestClass.addAll(this.node2ClassDescriptions.get(nt).keySet());

        for (FailedClass failedClass : failedClasses) {
            nonFailedTestClass.remove(failedClass);
        }

        return nonFailedTestClass;
    }

    /**
     * Note that we do not want to mark the individual tests themselves in the failed class as failed,
     * it would be misleading, so we create a 'task' for the test class and mark it failed. This is just
     * so that JUnit passes this information along and we actually see that something went wrong.
     */
    private void notifyRunnerAfterClassMethodsFailed(List<FailedClass> failedClasses,
                                                     RunNotifier runNotifier,
                                                     NodeType nt) {
        for (FailedClass failedClass : failedClasses) {
            Description testClassDescription = this.node2ClassDescriptions.get(nt).get(failedClass.failedClass);
            runNotifier.fireTestStarted(testClassDescription);
            runNotifier.fireTestFailure(new Failure(testClassDescription, failedClass.failure));
        }
    }

    private void notifyRunnerAllTestsInAllFailedClassesAreFailed(List<FailedClass> failedClasses,
                                                                 RunNotifier runNotifier,
                                                                 NodeType nt) {
        for (FailedClass failedClass : failedClasses) {
            notifyRunnerAllTestsInClassFailed(failedClass, runNotifier, nt);
        }
    }

    private void notifyRunnerAllTestsInClassFailed(FailedClass failedClass,
                                                   RunNotifier runNotifier,
                                                   NodeType nt) {
        Description testClassDescription = this.node2ClassDescriptions.get(nt).get(failedClass.failedClass);
        for (Description testDescription : testClassDescription.getChildren()) {
            runNotifier.fireTestStarted(testDescription);
            runNotifier.fireTestFailure(new Failure(testDescription, failedClass.failure));
        }
    }

    private List<FailedClass> runAllBeforeClassMethodsAndReturnFailedClasses(NodeType nt) {
        List<FailedClass> failedClasses = new ArrayList<>();

        for (Class<?> testClass : this.node2ClassDescriptions.get(nt).keySet()) {
            try {
                runBeforeClassMethodsIfAnyExists(testClass);
            } catch (Throwable e) {
                failedClasses.add(new FailedClass(testClass, e));
            }
        }

        return failedClasses;
    }

    private void runBeforeClassMethodsIfAnyExists(Class<?> testClass) {
        List<Method> beforeClassMethods = getBeforeClassMethods(testClass);

        for (Method beforeClassMethod : beforeClassMethods) {
            verifyBeforeClassMethodAnnotations(beforeClassMethod);
            try {
                beforeClassMethod.invoke(null);
            } catch (Throwable e) {
                throw new TestRunnerInitializationException("Error encountered in the @BeforeClass method: " + beforeClassMethod.getName(), e);
            }
        }
    }

    private List<Method> getBeforeClassMethods(Class<?> testClass) {
        List<Method> beforeClassMethods = new ArrayList<>();
        for (Method method : testClass.getMethods()) {
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

    private List<FailedClass> runAllAfterClassMethodsAndReturnFailedClasses(List<Class<?>> testClasses) {
        List<FailedClass> failedClasses = new ArrayList<>();

        for (Class<?> testClass : testClasses) {
            try {
                runAfterClassMethodsIfAnyExists(testClass);
            } catch (Throwable e) {
                failedClasses.add(new FailedClass(testClass, e));
            }
        }

        return failedClasses;
    }

    private void runAfterClassMethodsIfAnyExists(Class<?> testClass) {
        List<Method> afterClassMethods = getAfterClassMethods(testClass);

        for (Method afterClassMethod : afterClassMethods) {
            verifyAfterClassMethodAnnotations(afterClassMethod);
            try {
                afterClassMethod.invoke(null);
            } catch (Throwable e) {
                throw new TestRunnerInitializationException("Error encountered in the @AfterClass method: " + afterClassMethod.getName(), e);
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

    private List<Method> getAfterClassMethods(Class<?> testClass) {
        List<Method> afterClassMethods = new ArrayList<>();
        for (Method method : testClass.getMethods()) {
            if (method.isAnnotationPresent(org.junit.AfterClass.class)) {
                afterClassMethods.add(method);
            }
        }
        return afterClassMethods;
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
     * This is the description for the classes run under one NodeType.
     *
     * The returned description corresponds to the node.  Its children are the descriptions for each test class;
     * which in turn have children for each test method.
     *
     * Visual example:
     * <code>
     *     Rust description
     *     |--TestClassA description
     *     ||----testMethod1() description
     *     ||----testMethod2() description
     *     ||----testMethodN() description
     *     |--TestClassB description
     *     ||----testMethod1() description
     *     ||----testMethod2() description
     *     ||----testMethodN() description
     * </code>
     *
     * @param nt node type that this description is for
     * @param suiteClass the suite class that is being run (contains the test classes)
     * @param map will be populated with test classes and their description (the test
     *            classes are determined by what classes are referenced in the suiteClass)
     */
    private Description createSuiteDescriptionForNode(NodeType nt,
                                                      Class<?> suiteClass,
                                                      Map<Class<?>, Description> map) {
        Description suiteDescription = Description.createSuiteDescription(nt.name());

        List<Class<?>> testClasses = getSuiteTestClasses(suiteClass);
        for (Class<?> testClass : testClasses) {
            if(! helper.determineExcludedNodeTypes(testClass.getAnnotations()).contains(nt)) {
                Description description = deriveTestDescription(testClass, nt);
                suiteDescription.addChild(description);
                map.put(testClass, description);
            }
        }

        return suiteDescription;
    }

    /**
     * This is the description of all the test methods in the test class. JUnit uses the description
     * to display info in your IDE etc. about which test is which.
     */
    private Description deriveTestDescription(Class<?> testClass, NodeType nt) {
        Description description = Description.createTestDescription(testClass, testClass.getName());
        for (Method testMethod : testClass.getMethods()) {
            if (testMethod.isAnnotationPresent(org.junit.Test.class)) {
                description.addChild(Description.createTestDescription(testClass, nt.name()
                    + ":" + testMethod.getName()));
            }
        }
        return description;
    }

    private static List<Class<?>> getSuiteTestClasses(Class<?> suiteClass) {
        List<Class<?>> classes = new ArrayList<>();

        Annotation annotation = suiteClass.getAnnotation(org.junit.runners.Suite.SuiteClasses.class);
        for (Class<?> klazz : ((SuiteClasses) annotation).value()) {
            classes.add(klazz);
        }

        return classes;
    }

    private void verifyAnnotationsOfTestClasses() {
        for (Class<?> testClass : getSuiteTestClasses(suiteClass)) {
            verifyTestClassAnnotations(testClass);
        }
    }

    private void verifyTestClassAnnotations(Class<?> testClass) {
        for (Annotation annotation : testClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!annotationType.equals(org.junit.Ignore.class)
                && !annotationType.equals(org.junit.runner.RunWith.class)
                && !annotationType.equals(ExcludeNodeType.class)) {
                throw new UnsupportedAnnotation("This custom runner does not support the annotation: " + annotation);
            }
        }
    }

    private void verifyAnnotationsOfAllMethodsAndFields() {
        for (Class<?> testClass : getSuiteTestClasses(suiteClass)) {
            verifyMethodAndFieldAnnotations(testClass);
        }
    }

    private void verifyMethodAndFieldAnnotations(Class<?> testClass) {
        for (Method method : testClass.getDeclaredMethods()) {
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
        for (Field field : testClass.getDeclaredFields()) {
            for (Annotation annotation : field.getAnnotations()) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (!annotationType.equals(org.junit.Rule.class)) {
                    throw new UnsupportedAnnotation("This custom runner only supports the following field annotations: Rule. Found: " + annotation);
                }
            }
        }
    }

    private boolean isTestSuiteIgnored() {
        for (Annotation annotation : this.suiteClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.equals(org.junit.Ignore.class)) {
                return true;
            }
        }
        return false;
    }


}
