# Node Testing Harness
A test harness for functional integration testing of the Aion kernel(s).

This README documents details about the Test Harness library itself.  For a guide to getting started with executing and authoring test cases, start with the [Tests README.md](Tests/README.md).

## How to build and test Node Test Harness
We strive to make the `master` branch a ready-to-ship product at every commit, so it is always safe to build the latest version of the project. Otherwise, you can also find jars built at various commits in the `releases` tab.

The **first step** is to `git clone` the project to your machine.

Whether **testing a kernel** or **testing the harness**, you will need either an Aion Java kernel installed (untarred) in the `Tests/oan` directory, or an Aion Rust kernel installed (untarred) in the `Tests/aionr` directory.

The common case is **using the Node Test Harness to test a kernel or verify a new test of a kernel**.  To do this, run `./gradlew :Tests:test -i -PtestNodes=java` to test a Java node instance, or `./gradlew :Tests:test -i -PtestNodes=rust` to test a Rust node instance.

In the case of running a Rust kernel, it is a good idea to run the `custom.sh` script in the `aionr` folder once before running the tests to make sure you have all the required dependencies installed on your machine.

To **develop/test the Node Test Harness**, itself, run `./gradlew :TestHarness:test`.  Note that Gradle will build the `TestHarness.jar` required for testing a kernel, even if not explicitly built, so this step is only required when directly working on the harness.

# How to use the framework

## Contents
* [Configuring a local java kernel node](#configure-local-node)
    * [Building from source](#build-source)
    * [Using a pre-built kernel](#use-prebuilt)
* [Starting and stopping a local node](#start-and-stop-local)
* [Syncing to a network](#syncing)
* [Using a remote node](#remote-node)
    * [Creating a remote node](#remote-create)
    * [Using RPC with a remote node](#remote-rpc)
    * [Using NodeListener with a remote node](#remote-listening)
* [Creating transactions](#create-transaction)
    * [Creating a single transaction](#create-single-transaction)
    * [Creating transactions in bulk](#create-multiple-transactions)
* [Sending transactions to a local node](#send-transactions)
* [Waiting for transactions to process](#wait-for-transaction)
* [Collecting results in bulk](#bulk-results)
* [Sending and waiting on transactions properly](#send-and-wait)
* [Getting transaction receipts](#get-receipts)
* [Getting block numbers](#get-block-numbers)
* [Getting blocks transactions are sealed into](#get-blocks)
* [Making your own custom events](#make-custom-events)
    * [Making simple custom events](#make-simple-event)
    * [Making complex custom events](#make-complex-event)
    * [Using prepackaged events](#prepackaged-events)
    * [Determining which events were observed](#observation-info)
    * [Fetching the log lines that triggered an event](#get-logs)
* [How to benchmark events](#benchmarking)
* [Getting basic statistics](#statistics)

### <a name="configure-local-node">Configuring a local Java Kernel node</a>

#### <a name="use-prebuilt">Using a pre-built kernel</a>
You have a pre-built kernel. Not a tar file, but its extracted contents. This should be a directory named `oan` (most likely) that contains the `aion.sh` script. Now instead of building from source, you want the node to launch directly from this build.
```java
DatabaseOption databaseOption = DatabaseOption.DO_NOT_PRESERVE_DATABASE;
NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(network, "/path/to/build", databaseOption);

LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeFactory.NodeType.JAVA_NODE);
node.configure(configurations);
Result result = node.initialize();
```
Now each time we start up our node it will run directly from this build. Since we specified not to preserve the database, each time we __initialize__ the database will be deleted.
Caution: if there is no subsequent call to _initialize_ between `start()` and `stop()` the database will remain.

If we wanted to preserve the database we simply give the other database option: `DatabaseOption.PRESERVE_DATABASE`.

### <a name="start-and-stop-local">Starting and Stopping a Local Node</a>
Once you've configured and initialized the node it's time to start it up.
```java
Result result = node.start();
if (!result.isSuccess()) {
    System.out.println("Failed to start node: " + result.getError());
}

// do interesting stuff here.

node.stop();
```

You should __always__ check the result of `node.start()`. Don't assume the node was started correctly. Furthermore, you should __always__ stop the node once you're done with it. The node runs as an external process and it is not guaranteed that it will be shutdown just because your program has exited. This is a common reason why `node.start()` fails: a previous node was not shutdown and now the two are attempting to use the same database or listen on the same port, etc.

#### When is it safe to use a newly started node?
If you are connecting to a real network then it is always recommended that you wait until you have synced up with that network. However, if you are running a standalone node (you have no peers) then you definitely do not want to sync (this will be explained below), but the rpc server and the miner threads are not necessarily ready to be prodded yet - this is a known "bug" and is being tracked by issue #35. Until the bug is fixed, it is recommended that you sleep for 5-10 seconds just to be safe before proceeding.

### <a name="remote-node">Interacting with a remote node</a>
#### <a name="remote-create">i. Creating a remote node</a>
Let's assume we've got some remote node running on some server. We don't actually have to do anything special to create our `RemoteNode` representation of it. The "specialness" comes into play when we try to listen to that node with the `NodeListener` or using RPC, as we see below. To actually get an instance of a `RemoteNode` we just do the following:
```java
RemoteNode node = NodeFactory.getNewRemoteNodeInstance(NodeType.JAVA);
```
This is a generic remote node, it is not actually "linked to" any existing remote node yet. We'll see how to do that below when `NodeListener` comes into play.

#### <a name="remote-rpc">ii. Using RPC with a remote node</a>
To send events off to a remote node via the `RPC` class we don't need to do anything special. When the `RPC` instance is created, just give it the correct IP and port to listen to. That's it.

#### <a name="remote-listening">iii. Using NodeListener with a remote node</a>
To listen to the log file of a remote node you just need some way of obtaining that log file, or its output stream in general (which you can then redirect to a log file). There are future plans to deal in terms of streams but for now the stdout stream is observed via a file.
```java
File remoteOutput = // The stdout of the remote node, which is being written to this file.

RemoteNode node = NodeFactory.getNewRemoteNodeInstance(NodeType.JAVA);
node.connect(remoteOutput);

// Do some interesting stuff...

node.disconnect();
```
This is the only time we actually "link" a `RemoteNode` to the actual remote node it represents, and all we are doing it linking it up to the output stream of that node. When we `disconnect()` we have our generic `RemoteNode` back and we can use it again to `connect()` to a different log file representing a different remote node.

### <a name="create-transaction">Creating Transactions</a>
#### <a name="create-single-transaction">i. Creating single Transaction objects</a>
Most interesting interactions with a node are done via sending it a `RawTransaction`. You can construct a `RawTransaction` as follows:
```java
PrivateKey key = PrivateKey.random(); // The private key of the sender
BigInteger nonce = BigInteger.ZERO;
Address dest = null; // The destination/recipient address
byte[] data = // your data
long limit = 2_000_000; // The energy limit
long price = 10_000_000_000L; // The energy price
BigInteger value = BigInteger.ZERO;

TransactionResult result;

if (transactionIsForFvm) {
    // A transaction that will be run on the FVM
    result = Transaction.buildAndSignFvmTransaction(key, nonce, dest, data, limit, price, value);
} else {
    // A transaction that will be run on the AVM
    result = Transaction.buildAndSignAvmTransaction(key, nonce, dest, data, limit, price, value);
}

if (!result.isSuccess()) {
    System.out.println("Failed to construct the transaction: " + result.getError());
}

RawTransaction transaction = result.getTransaction();
```

The test harness is programmed in a defensive style: if it knows you are making a mistake then it tells you. A transaction will fail to be built if it is supplied nonsensical data (a `null` private key, a negative energy limit, etc.), so it's always worth checking the result.

#### <a name="create-multiple-transactions">ii. Creating multiple Transaction objects in bulk</a>
Often we aren't too interesting in only sending a single transaction, we want to send a whole bunch. The test harness has a very flexible builder class that can be used to construct transactions in bulk, and this is definitely the recommended way of doing so.

Every field that can be specified in a transaction can either be reused by all transactions or can be unique for them all. Let's say we want to send 10 transactions from the same sender address but to 10 different recipient addresses, and we want to transfer a different amount of funds to each of these recipients. Here's how we can easily construct these transactions:
```java
PrivateKey senderKey = PrivateKey.random();
BigInteger senderInitialNonce = BigInteger.ZERO;
List<Address> beneficiaries = // Produce all of the recipient addresses
List<BigInteger> amounts = // Produce all of the amounts to transfer

BulkResult<Transaction> result = new BulkRawTransactionBuilder(10)
    .useSameSender(senderKey, senderInitialNonce)
    .useMultipleDestinations(beneficiaries)
    .useMultipleTransferValues(amounts)
    .useSameTransactionData(new byte[0])
    .useSameEnergyLimit(21_000)
    .useSameEnergyPrice(10_000_000_000L)
    .useSameTransactionType(TransactionType.FVM)
    .build();
    
if (!result.isSuccess()) {
    System.out.println("Failed to construct the bulk transactions: " + result.getError());
}

List<RawTransaction> transactions = result.getResults();
```

This will create 10 transactions all from the same sender address. The first transaction will have a nonce equal to `senderInitialNonce`, and each subsequent transaction will have a nonce that is one larger than the previous. The `Address` at index `i` in `beneficiaries` will be the recipient of the `i`'th transaction and will be transfered the amount at index `i` in `amounts`. All transactions will have the same specified energy limit and price and all will be executed by the FVM.

### <a name="send-transactions">Sending Transactions to a Local Node</a>
Now that we've built our transaction or transactions, it's time to send them off to the node.
```java
RPC rpc = new RPC("127.0.0.1", "8545");

// Sending a single transaction.
RawTransaction transaction = // The transaction built from the step above.
RpcResult<ReceiptHash> singleResult = rpc.sendTransaction(transaction);
if (!singleResult.isSuccess()) {
    System.out.println("Failed to send the transaction: " + singleResult.getError());
}

// Sending transactions in bulk.
List<RawTransaction> transactions = // The transactions built from the step above.
List<RpcResult<ReceiptHash> bulkResult = rpc.sendTransactions(transactions);
if (!bulkResult.isSuccess()) {
    System.out.println("Failed to send the transactions: " + bulkResult.getError());
}
```
This is typically not the full pattern you would use when sending a transaction. Usually you would want to listen for an event, such as waiting for the transaction to be processed. We will put that all together once we show you how to listen for a transaction to be processed.

### <a name="wait-for-transaction">Waiting for a transaction to be processed</a>
Sending a transaction is an asynchronous event. The transaction hash that it returns is meaningless until the transaction has been processed. Here's how to wait until that happens:
```java
LocalNode node = // A local node obtained from one of the steps above.
NodeListener listener = NodeListener.listenTo(node);

// Listen for a single transaction to be processed.
RawTransaction transaction = // The transaction built from the step above.
FutureResult<LogEventResult> future = listener.listenForTransactionToBeProcessed(transaction, 1, TimeUnit.MINUTES);
LogEventResult result = future.get();
if (!result.eventWasObserved()) {
    System.out.println("Failed to observe the transaction being processed: " + result.getError());
}

// Listen for multiple transactions to be processed.
List<RawTransaction> transactions = // The transactions built from the step above.
List<FutureResult<LogEventResult>> futures = listener.listenForTransactionsToBeProcessed(transactions, 1, TimeUnit.MINUTES);
TestHarnessHelper.waitOnFutures(futures);

// This is NOT the best way to do this, below we show you how to collect the results in a single step
for (FutureResult<LogEventResult> futureResult : futures) {
    LogEventResult eventResult = futureResult.get(); // This method will no longer block, we've already waited.
    if (!eventResult.eventWasObserved()) {
        System.out.println("Failed to observe the transaction being processed: " + eventResult.getError());
    }
}
```
We create a new `NodeListener` so that we can listen to the node's log file. Then we tell the listener to listen for the transaction to be processed (either it is sealed into a block or it is rejected) and we will timeout after 1 minute of waiting for this event. The listener returns a `Future` which we can then block on when we call `future.get()` to wait for its result.

In the case where we submitted multiple transactions to be listened to, we can use the `TestHarnessHelper` class to wait on all of the futures to complete. This method blocks until all of the futures are finished. After this we can collect their results.

Note that when we wait on multiple events, the timeout value we provide is applied to each of the events. The timeout value should be large enough, then, to wait for all of the events to finish.

### <a name="bulk-results">Collecting bulk results</a>
So you've called a method that has returned a list of results of some sort and you want to do two things: 1) check that all of the results were successful, 2) extract the meaningful part of the results into a list. We've got an easy way to do that for you.
```java
List<FutureResult<LogEventResult>> futures = // The futures we obtained in the step above.
BulkResult<LogEventResult> bulkResult = TestHarnessHelper.extractResults(futures);
if (!bulkResult.isSuccess()) {
    System.out.println("At least one of the results failed: " + bulkResult.getError());
}
List<LogEventResult> results = bulkResult.getResults();
```

The `TestHarnessHelper.extractResults` method will pull all of the `LogEventResult` objects into a single list, which can be obtained via the `bulkResult.getResults()` call, but this method will also return an unsuccessful result if any one of the results being extracted was unsuccessful. In other words, if `bulkResult.isSuccess()` is `true`, then all of the results themselves were successful.

### <a name="send-and-wait">Sending a Transaction and Waiting for it to be Processed</a>
Let's put all of this together now. First we'll walk through how to send a single transaction and wait for it to be processed.
```java
LocalNode node = // A local node obtained from one of the steps above.

RPC rpc = new RPC("127.0.0.1", "8545");
NodeListener listener = NodeListener.listenTo(node);

RawTransaction transaction = // The transaction built from the step above.

// Start listening for the transaction before we send it so we know we don't miss it.
FutureResult<LogEventResult> future = listener.listenForTransactionToBeProcessed(transaction, 1, TimeUnit.MINUTES);

// Send the transaction off.
RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
if (!sendResult.isSuccess()) {
    System.out.println("Failed to send the transaction: " + sendResult.getError());
    // You probably do not want to continue now.
}

// Wait for the transaction to be processed.
LogEventResult futureResult = future.get();
if (!futureResult.eventWasObserved()) {
    System.out.println("Failed to observe the transaction being processed: " + futureResult.getError());
}
```
It is vital to start listening for the event before the event could even happen, that way we know that we will observe it. Once all of this code executes and if none of the results were unsuccessful, we can be sure that we sent the transaction and that the transaction was sealed into a block.

Let's apply the same idea now to transactions in bulk.
```java
LocalNode node = // A local node obtained from one of the steps above.

RPC rpc = new RPC("127.0.0.1", "8545");
NodeListener listener = NodeListener.listenTo(node);

List<RawTransaction> transactions = // The transactions built from the step above.

// Start listening for the transactions before we send them so we know we don't miss them.
List<FutureResult<LogEventResult>> futures = listener.listenForTransactionsToBeProcessed(transactions, 1, TimeUnit.MINUTES);

// Send the transactions off.
List<RpcResult<ReceiptHash>> sendResults = rpc.sendTransactions(transaction);
BulkResult<ReceiptHash>> bulkResults = TestHarnessHelper.extractResults(sendResults);
if (!bulkResults.isSuccess()) {
    System.out.println("Failed to send all of the transactions: " + bulkResults.getError());
    // You probably do not want to continue now.
}

// Wait for the transactions to be processed.
TestHarnessHelper.waitOnFutures(futures);
BulkResult<LogEventResult> bulkEventResults = TestHarnessHelper.extractFutures(futures);
if (!bulkEventResults.isSuccess()) {
    System.out.println("Failed to observe the transaction being processed: " + bulkEventResults.getError());
}
```

### <a name="get-receipts">Getting the transaction receipts</a>
Okay, so we've sent off our transactions to the node and we've observed them being processed. But now we've got these `ReceiptHash` objects hanging around, and what do we do with them? You can use these `ReceiptHash`es to get their corresponding `TransactionReceipt`s.
```java
RPC rpc = new RPC("127.0.0.1", "8545");

ReceiptHash hash = // The receipt hash we obtained in the step above.

RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
if (!receiptResult.isSuccess()) {
    System.out.println("Failed to get the transaction receipt: " + receiptResult.getError());
}

TransactionReceipt receipt = receiptResult.getResult();
```
It's just one more RPC call. Since we know the transaction has been processed, we can safely get the transaction receipt. If we are not yet sure that the transaction has been processed, then the receipt hash may not yet correspond to any transaction receipt and our attempt will be unsuccessful.

And here's how we do it in the bulk case:
```java
RPC java = new RPC("127.0.0.1", "8545");

List<ReceiptHash> hashes = // The receipt hashes we obtained in the step above.

List<RpcResult<TransactionReceipt>> receiptResults = rpc.getTransactionReceipts(hashes);
BulkResult<TransactionReceipt> bulkResults = TestHarnessHelper.extractResults(receiptResults);
if (!bulkResults.isSuccess()) {
    System.out.println("Failed to get all of the transaction receipts: " + bulkResults.getError());
}

List<TransactionReceipt> receipts = bulkResults.getResults();
```
Like above, if we want this to work, we have to have observed all of the transactions being processed first.

### <a name="get-block-numbers">Getting the block numbers that the transactions were sealed into</a>
We've sent our transactions off and we've got the transaction receipts back. Our transactions have been sealed into one or more blocks on the blockchain, but we want to know which ones. And from here, we then want to get the blocks themselves.

```java
// Getting the block number of a single transaction.
TransactionReceipt receipt = // The receipt we obtained from the step above.
BigInteger blockNumber = receipt.getBlockNumber();

// Getting the block numbers of multiple transactions.
List<TransactionReceipt> receipts = // The receipts we obtained from the step above.
List<BigInteger> blockNumbers = TestHarnessHelper.extractBlockNumbers(receipts);
```

### <a name="get-blocks">Getting the Blocks that the transactions were sealed into</a>
We now have the block number or numbers of all the blocks that our transactions were sealed into. Let's get the `Block` object itself.

```java
RPC rpc = new RPC("127.0.0.1", "8545");

// Getting a single block.
BigInteger blockNumber = // The number we obtained from the step above.
RpcResult<Block> blockResult = rpc.getBlockByNumber(blockNumber);
if (!blockResult.isSuccess()) {
    System.out.println("Failed to get the block: " + blockResult.getError());
}
Block block = blockResult.getResult();

// Getting multiple blocks.
List<BigInteger> blockNumbers = // The numbers we obtained from the step above.
List<RpcResult<Block>> blockResults = rpc.getBlocksByNumber(blockNumbers);
BulkResult<Block> bulkResult = TestHarnessHelper.extractResults(blockResults);
if (!bulkResult.isSuccess()) {
    System.out.println("Failed to get the blocks: " + bulkResult.getError());
}
List<Block> blocks = bulkResult.getResults();
```

### <a name="make-custom-events">Making your own custom events to listen for</a>
#### <a name="make-simple-event">i. Making a simple event</a>
The `NodeListener` class has some basic events it will listen for, but it also has a general method for listening for any event you want to give it. These are strictly log events: you use the `NodeListener` to listen for specific strings in the output log. The log file is read line by line, so your string has to be something that is actually logged on the same line. If your string actually occurs over multiple lines then you want to make a complex event (we cover that below).

Here's how you make your own simple event and listen for it.
```java
LocalNode node = // A local node obtained from one of the steps above.
NodeListener listener = NodeListener.listenTo(node);

IEvent event = new Event("your string to listen for");
FutureResult<LogEventResult> future = listener.listenForEvent(event, 1, TimeUnit.MINUTES);
LogEventResult result = future.get();
```

#### <a name="make-complex-event">ii. Making complex events</a>
A simple event may not be sufficient for you. Maybe you've got the following scenario: if one part of your contract logic is triggered then you expect the following 3 separate lines to be printed to screen: "apple", "banana", "peach". But if another part is triggered, you expect to see the following 2 separate lines printed: "big", "small". However, all you actually care about at the moment is whether or not either of these two events were triggered. Let's see how it's done.
```java
LocalNode node = // A local node obtained from one of the steps above.
NodeListener listener = NodeListener.listenTo(node);

IEvent fruits = Event.and(new Event("apple"), Event.and("banana", "peach"));
IEvent sizes = Event.and("big", "small");
IEvent event = Event.or(fruits, sizes);

FutureResult<LogEventResult> future = listener.listenForEvent(event, 1, TimeUnit.MINUTES);
LogEventResult result = future.get();
```
This event will be observed only if the `fruits` event is observed _or_ the `sizes` event is. The `fruits` event is observed only if "apple" _and_ "banana" _and_ "peach" are all observed. The `sizes` event is observed only if "big" _and_ "small" are both observed.

You can use these and-or operators to create arbitrarily complex events to be listened for.

#### <a name="prepackaged-events">iii. Prepackaged events</a>
The events that the `NodeListener` allows you to listen for by default can be found in the subclasses of the `PrepackagedLogEvents` interface if you want to handle them directly. This may be desirable if you want to do something like: listen for a transaction to be processed _and_ listen for specific output it will generate.

A quick example of this:
```java
PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();
prepackagedLogEventsFactory.setKernel(JAVA_NODE); // or RUST_NODE
LocalNode node = // A local node obtained from one of the steps above.
NodeListener listener = NodeListener.listenTo(node);
Transaction transaction = // A transaction that was built from one of the above steps.

IEvent output = new Event("contract output");
IEvent transactionProcessed = prepackagedLogEventsFactory.build().getTransactionProcessedEvent(transaction);
IEvent event = Event.and(output, transactionProcessed);

FutureResult<LogEventResult> future = listener.listenForEvent(event, 1, TimeUnit.MINUTES);
LogEventResult result = future.get();
```

#### <a name="observation-info">iv. How to determine which events were observed</a>
Let's take the example above, we have a complex event that we have defined as follows:
```java
IEvent apple = new Event("apple");
IEvent banana = new Event("banana");
IEvent peach = new Event("peach");
IEvent bananaAndPeach = Event.and(banana, peach);
IEvent fruits = Event.and(apple, bananaAndPeach);

IEvent big = new Event("big");
IEvent small = new Event("small");
IEvent sizes = Event.and(big, small);

IEvent event = Event.or(fruits, sizes);
```
The `NodeListener` has confirmed that `event` was observed. But `event` is a complex event constructed out of multiple simple events, and we want to know which of these simple events actually occurred? Or perhaps we want to know whether it was `fruits` or `sizes` that was observed.

This is all we have to do:
```java
if (fruits.hasBeenObserved()) {
    System.out.println("Observed the event: fruits");
} else {
    System.out.pritnln("Observed the event: sizes");
}

System.out.println("All events that were observed: " + event.getAllObservedEvents());
```
We just use the `hasBeenObserved()` method.
If we want to get a list of all the `String`s that have been seen then we can call `getAllObservedEvents()`.

#### <a name="get-logs">v. Fetching the log lines that triggered an event</a>
Again, let's say we have the same complex event as defined in the step above. Now we want to know what lines in the log file actually caused these events to be observed. Perhaps we are doing some log monitoring and the line contains variable information but we know these static keywords will capture that line, and we can then parse it once we have it.

Here's a sample of the log file that caused our event to be observed:
```
Count for apple: 17.
Count for banana: 6.
Count for peach: 111.
```
These lines caused the `fruits` event defined above to be observed. But now we want to retrieve these log lines back. Here's how:

```java
List<String> logLines = fruits.getAllObservedLogs();
```
Now we can parse out the `17`, `6`, and `111` from the lines that we were after.

### <a name="benchmarking">Benchmarking events</a>
We have some events we want to listen for, but we're actually most interesting in how long it takes for these events to occur. Let's take the simple example of a transaction being processed. What we want to measure is the amount of time it takes from sending the transaction over RPC until it gets sealed into a block. This information is already available to you.

```java
LocalNode node = // A local node obtained from one of the steps above.

RPC rpc = new RPC("127.0.0.1", "8545");
NodeListener listener = NodeListener.listenTo(node);

RawTransaction transaction = // A transaction built from one of the steps above.

FutureResult<LogEventResult> future = listener.listenForTransactionToBeProcessed(transaction);

RpcResult<ReceiptHash> result = rpc.sendTransaction(transaction);
if (!result.isSuccess()) {
    System.out.println("Failed to send the transaction: " + result.getError());
}

LogEventResult logResult = future.get();
if (!logResult.eventWasObserved()) {
    System.out.println("Failed to observe the transaction being processed: " + logResult.getError());
}

long sendTime = result.getTimeOfCall(TimeUnit.NANOSECONDS);
long observedTime = logResult.timeOfObservation(TimeUnit.NANOSECONDS);

long duration = observedTime - sendTime;
```
This gives us the time, in nanoseconds, at which the RPC call was made to send the transaction, and the time at which the `NodeListener` observed the transaction being processed. Internally, these measurements are made using `System.nanoTime()` so no information is lost when extracting these times in nanoseconds.

### <a name="statistics">Getting some basic statistics</a>
Let's say we've sent off a batch of transactions to be processed and we listened for them to be observed. Even more, we obtained all of the `Block`s that these transactions were sealed into (see the above steps). Now let's get some interesting information out of all of this.

Above we saw how to get the duration that it took for an event to be sent over the RPC and then observed in the log file. Let's see how to do this for multiple events.
```java
List<RpcResult<?>> rpcResults = // rpc results obtained somehow
List<FutureResult<LogEventResult>> futureResults = // futures obtained somehow

long[] sendTimes = TestHarnessHelper.extractResultTimestamps(rpcResults, TimeUnit.NANOSECONDS);
long[] observedTimes = TestHarnessHelper.extractFutureTimestamps(futureResults, TimeUnit.NANOSECONDS);

DurationStatistics durationStats = DurationStatistics.from(sendTimes, observedTimes);
durationStats.printStatistics();
```

Now let's assume we've sent a bunch of transactions off and we want to see how many transactions made it into each block. For example, 100 transactions were sent and they were sealed into 8 blocks, but what we want to know is the distribution.
```java
List<TransactionReceipt> receipts = // transaction receipts obtained somehow
TransactionStatistics transactionStats = TransactionStatistics.from(receipts);
transactionStats.printStatistics();
```

Finally, we want some better statistics on block saturation in terms of energy. Again, let's assume we have 100 transactions that were sealed into 8 blocks. We now know how many transactions were put in each block, and that's useful, but now we want some hard numbers representing the energy limits of these blocks and how much of this limit was used by by the transactions sealed into it. In other words, we want more information about the energy distribution over these blocks.
```java
int numberOfTransactionsSent = 100;
List<Block> blocks = // blocks obtained somehow

BlockStatistics blockStats = BlockStatistics.from(numberOfTransactionsSent, blocks);
blockStats.printStatistics();
```
