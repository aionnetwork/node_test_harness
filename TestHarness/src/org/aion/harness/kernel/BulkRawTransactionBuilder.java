package org.aion.harness.kernel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.harness.result.BulkResult;

/**
 * A builder class that makes the process of constructing a bulk number of raw transactions easier.
 *
 * A new instance of the builder should be created for each new batch of bulk raw transactions to be
 * created. This is not strictly necessary if no mutually-incompatible fields are set, but does make
 * reasoning about the state of the constructed transactions far easier.
 *
 * Each part of the transaction can either be set using a single value that will be used by all
 * transactions or else a list of unique values can be specified per transaction. These
 * single-multiple builder pairs are mutually exclusive and only one or the other can ever be
 * invoked, otherwise an exception is thrown.
 *
 * If a single method is invoked multiple times and none of these invocations causes an exception
 * to be thrown, then the latest invocation takes precedence.
 *
 * The build method may be invoked multiple times.
 */
public final class BulkRawTransactionBuilder {
    private final int numTransactions;
    private BigInteger initialNonce = null;
    private BigInteger value = null;
    private PrivateKey senderKey = null;
    private boolean singleDestinationSpecified = false;
    private Address destination = null;
    private byte[] data = null;
    private boolean singleEnergyLimitSpecified = false;
    private long energyLimit = -1;
    private boolean singleEnergyPriceSpecified = false;
    private long energyPrice = -1;
    private TransactionType type = null;

    private List<BigInteger> nonces = null;
    private List<BigInteger> values = null;
    private List<PrivateKey> senderKeys = null;
    private List<Address> destinations = null;
    private List<byte[]> datas = null;
    private List<Long> energyLimits = null;
    private List<Long> energyPrices = null;
    private List<TransactionType> types = null;

    public enum TransactionType { AVM, FVM }

    /**
     * Constrcuts a new bulk transaction builder that will be used to build the specified number of
     * transactions.
     *
     * @param numberOfTransactions The number of transactions to build.
     */
    public BulkRawTransactionBuilder(int numberOfTransactions) {
        if (numberOfTransactions < 0) {
            throw new IllegalArgumentException("Cannot build a negative number of transactions.");
        }

        this.numTransactions = numberOfTransactions;
    }

    /**
     * Causes all transactions to be sent from the same sender whose private key is the specified
     * key.
     *
     * The transaction nonces will be created in incrementing order beginning at the initial nonce
     * value specified.
     *
     * <b>If this method is invoked then {@code useMultipleSenders()} is prohibited. These two
     * methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param senderKey The key of the sender account.
     * @param initialNonce The initial nonce.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useSameSender(PrivateKey senderKey, BigInteger initialNonce) {
        if ((this.senderKeys != null) || (this.nonces != null)) {
            throw new IllegalStateException("A list of senders has already been specified. Cannot also set a single sender.");
        }
        if (senderKey == null) {
            throw new NullPointerException("Cannot set a null sender key.");
        }
        if (initialNonce == null) {
            throw new NullPointerException("Cannot set a null initial nonce.");
        }

        this.senderKey = senderKey;
        this.initialNonce = initialNonce;
        return this;
    }

    /**
     * Causes all transactions to be sent from the addresses whose private keys are specified here.
     * The i'th transaction will be signed by the i'th private key.
     *
     * It is also assumed that the i'th nonce listed in {@code nonces} corresponds to the i'th
     * private key.
     *
     * <b>If this method is invoked then {@code useSameSender()} is prohibited. These two
     * methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param senderKeys The keys of the sender accounts.
     * @param nonces The nonces of the sender accounts.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useMultipleSenders(List<PrivateKey> senderKeys, List<BigInteger> nonces) {
        if ((this.senderKey != null) || (this.initialNonce != null)) {
            throw new IllegalStateException("A single sender has already been specified. Cannot also set multiple senders.");
        }
        if (senderKeys == null) {
            throw new NullPointerException("Cannot set a null list of sender keys.");
        }
        if (nonces == null) {
            throw new NullPointerException("Cannot set a null list of nonces.");
        }

        this.senderKeys = new ArrayList<>(senderKeys);
        this.nonces = new ArrayList<>(nonces);
        return this;
    }

    /**
     * Causes all transactions to send the exact same amount of value to their recipients, which is
     * the value specified here.
     *
     * <b>If this method is invoked then {@code useMultipleTransferValues()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param value The value to send in all transactions.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useSameTransferValue(BigInteger value) {
        if (this.values != null) {
            throw new IllegalStateException("Multiple transfer values have already been specified. Cannot also set a single value.");
        }
        if (value == null) {
            throw new NullPointerException("Cannot set a null value.");
        }

        this.value = value;
        return this;
    }

    /**
     * Causes all transactions to send unique amounts of value to their recipients, such that the
     * i'th transaction will send the value at index i in {@code values};
     *
     * <b>If this method is invoked then {@code useSameTransferValue()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param values The values to send in each transaction.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useMultipleTransferValues(List<BigInteger> values) {
        if (this.value != null) {
            throw new IllegalStateException("A single transfer value has already been specified. Cannot also set multiple values.");
        }
        if (values == null) {
            throw new NullPointerException("Cannot set a null list of values.");
        }

        this.values = new ArrayList<>(values);
        return this;
    }

    /**
     * Causes all transactions to use the same destination address as the recipient, which is the
     * specified destination.
     *
     * <b>If this method is invoked then {@code useMultipleDestinations()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param destination The destination for all transactions.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useSameDestination(Address destination) {
        if (this.destinations != null) {
            throw new IllegalStateException("Multiple destinations have already been specified. Cannot also set a single destination.");
        }

        this.destination = destination;
        this.singleDestinationSpecified = true;
        return this;
    }

    /**
     * Causes all transactions to use a unique destination address as its recipient, such that the
     * i'th transaction will use the destination at index i in {@code destinations}.
     *
     * <b>If this method is invoked then {@code useSameDestination()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param destinations The destinations for each transaction.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useMultipleDestinations(List<Address> destinations) {
        if (this.singleDestinationSpecified) {
            throw new IllegalStateException("A single destination has already been specified. Cannot also set multiple destinations.");
        }
        if (destinations == null) {
            throw new NullPointerException("Cannot set a null list of destinations.");
        }

        this.destinations = new ArrayList<>(destinations);
        return this;
    }

    public BulkRawTransactionBuilder useSameTransactionData(byte[] data) {
        if (this.datas != null) {
            throw new IllegalStateException("Multiple transaction datas have already been specified. Cannot also set a single data input.");
        }
        if (data == null) {
            throw new NullPointerException("Cannot set a null data.");
        }

        this.data = Arrays.copyOf(data, data.length);
        return this;
    }

    public BulkRawTransactionBuilder useMultipleTransactionDatas(List<byte[]> datas) {
        if (this.data != null) {
            throw new IllegalStateException("A single transaction data has already been specified. Cannot also set multiple data.");
        }
        if (datas == null) {
            throw new NullPointerException("Cannot set a null list of data.");
        }

        this.datas = copyDataList(datas);
        return this;
    }

    /**
     * Causes all transactions to use the same energy limit, which is the limit specified here.
     *
     * <b>If this method is invoked then {@code useMultipleEnergyLimits()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param energyLimit The energy limit for all transactions.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useSameEnergyLimit(long energyLimit) {
        if (this.energyLimits != null) {
            throw new IllegalStateException("Multiple energy limits have already been specified. Cannot also set a single limit.");
        }

        this.energyLimit = energyLimit;
        this.singleEnergyLimitSpecified = true;
        return this;
    }

    /**
     * Causes all transactions to use a unqiue energy limit, such that the i'th transaction will use
     * the energy limit at index i in {@code energyLimits}.
     *
     * <b>If this method is invoked then {@code useSameEnergyLimit()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param energyLimits The energy limits for each transaction.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useMultipleEnergyLimits(List<Long> energyLimits) {
        if (this.singleEnergyLimitSpecified) {
            throw new IllegalStateException("A single energy limit has already been specified. Cannot also set multiple energy limits.");
        }
        if (energyLimits == null) {
            throw new NullPointerException("Cannot set a null list of energy limits.");
        }

        this.energyLimits = new ArrayList<>(energyLimits);
        return this;
    }

    /**
     * Causes all transactions to use the same energy price, which is the price specified here.
     *
     * <b>If this method is invoked then {@code useMultipleEnergyPrices()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param energyPrice The energy price for all transactions.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useSameEnergyPrice(long energyPrice) {
        if (this.energyPrices != null) {
            throw new IllegalStateException("Multiple energy prices have already been specified. Cannot also set a single price.");
        }

        this.energyPrice = energyPrice;
        this.singleEnergyPriceSpecified = true;
        return this;
    }

    /**
     * Causes all transactions to use a unqiue energy price, such that the i'th transaction will use
     * the energy price at index i in {@code energyPrices}.
     *
     * <b>If this method is invoked then {@code useSameEnergyPrice()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param energyPrices The energy prices for each transaction.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useMultipleEnergyPrices(List<Long> energyPrices) {
        if (this.singleEnergyPriceSpecified) {
            throw new IllegalStateException("A single energy price has already been specified. Cannot also set multiple energy prices.");
        }
        if (energyPrices == null) {
            throw new NullPointerException("Cannot set a null list of energy prices.");
        }

        this.energyPrices = new ArrayList<>(energyPrices);
        return this;
    }

    /**
     * Causes all transactions to use the same transaction type, which is the type specified here.
     *
     * <b>If this method is invoked then {@code useMultipleTransactionTypes()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param type The transaction type for all transactions.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useSameTransactionType(TransactionType type) {
        if (this.types != null) {
            throw new IllegalStateException("Multiple transaction types have already been specified. Cannot also set a single type.");
        }
        if (type == null) {
            throw new NullPointerException("Cannot set a null transaction type.");
        }

        this.type = type;
        return this;
    }

    /**
     * Causes all transactions to use a unqiue transaction type, such that the i'th transaction will
     * use the i'th transaction type in {@code types}.
     *
     * <b>If this method is invoked then {@code useSameTransactionType()} is prohibited. These
     * two methods are mutually exclusive because they are entirely incompatible.</b>
     *
     * @param types The transaction types for each transaction.
     * @return this builder.
     */
    public BulkRawTransactionBuilder useMultipleTransactionTypes(List<TransactionType> types) {
        if (this.type != null) {
            throw new IllegalStateException("A single transaction type has already been specified. Cannot also set multiple types.");
        }
        if (types == null) {
            throw new NullPointerException("Cannot set a null list of transaction types.");
        }

        this.types = new ArrayList<>(types);
        return this;
    }

    /**
     * Constructs the number of transactions specified by the builder according to all of the options
     * set by the various builder methods.
     *
     * If any transaction in the batch fails to be created then this method will not return any
     * transactions but instead an error message related to the failed creation.
     *
     * Otherwise, if all transactions are made correctly, this method will return a successful
     * {@link BulkResult} that will contain all of the newly constructed transactions.
     *
     * @return a result indicating whether or not the transactions were created, and if so, holds the
     * transactions themselves.
     */
    public BulkResult<SignedTransaction> build() {
        // Ensure that if any list option was specified, that it has the expected size.
        if ((this.senderKeys != null) && (this.senderKeys.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of sender keys: " + this.senderKeys.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.nonces != null) && (this.nonces.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of nonces: " + this.nonces.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.values != null) && (this.values.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of value transfers: " + this.values.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.destinations != null) && (this.destinations.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of destination addresses: " + this.destinations.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.datas != null) && (this.datas.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of transaction data inputs: " + this.datas.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.energyLimits != null) && (this.energyLimits.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of energy limits: " + this.energyLimits.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.energyPrices != null) && (this.energyPrices.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of energy prices: " + this.energyPrices.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }
        if ((this.types != null) && (this.types.size() != this.numTransactions)) {
            throw new IllegalStateException("Specified incorrect number of transaction types: " + this.types.size()
                + ", but there are " + this.numTransactions + " transactions to be made!");
        }

        // Create the transactions.
        List<SignedTransaction> transactions = new ArrayList<>();

        SignedTransaction transaction;
        BigInteger nonce = this.initialNonce;
        for (int i = 0; i < this.numTransactions; i++) {

            TransactionType type = (this.type == null) ? this.types.get(i) : this.type;
            PrivateKey key = (this.senderKey == null) ? this.senderKeys.get(i) : this.senderKey;
            BigInteger senderNonce = (this.initialNonce == null) ? this.nonces.get(i) : nonce;
            Address destination = (this.singleDestinationSpecified) ? this.destination : this.destinations.get(i);
            byte[] data = (this.data == null) ? this.datas.get(i) : this.data;
            long energyLimit = (this.singleEnergyLimitSpecified) ? this.energyLimit : this.energyLimits.get(i);
            long energyPrice = (this.singleEnergyPriceSpecified) ? this.energyPrice : this.energyPrices.get(i);
            BigInteger value = (this.value == null) ? this.values.get(i) : this.value;

            // Construct the appropriate transaction based on the type.
            try {
                if ((type == TransactionType.AVM) && (destination == null)) {
                    transaction = SignedTransaction
                        .newAvmCreateTransaction(key, senderNonce, data, energyLimit, energyPrice, value,
                            null);
                } else {
                    transaction = SignedTransaction
                        .newGeneralTransaction(key, senderNonce, destination, data, energyLimit, energyPrice, value,
                            null);
                }
            } catch (Exception e) {
                return BulkResult.unsuccessful("Failed to create transaction #" + i + " due to: " + e.getMessage());
            }

            transactions.add(transaction);

            // Increment our initial nonce only if this option was specified.
            if (this.initialNonce != null) {
                nonce = nonce.add(BigInteger.ONE);
            }
        }

        return BulkResult.successful(transactions);
    }

    private List<byte[]> copyDataList(List<byte[]> datas) {
        List<byte[]> copy = new ArrayList<>();
        for (byte[] data : datas) {
            copy.add(Arrays.copyOf(data, data.length));
        }
        return copy;
    }

}
