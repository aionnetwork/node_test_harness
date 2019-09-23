package org.aion.harness.kernel;

import java.util.Arrays;
import main.SignedTransactionBuilder;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import org.apache.commons.codec.binary.Hex;

/**
 * A signed Aion transaction.
 *
 * This class is immutable.
 */
public final class SignedTransaction {
    private final byte[] transactionBytes;
    private byte[] hash;

    private SignedTransaction(PrivateKey sender, BigInteger nonce, Address destination, byte[] data,
        long energyLimit, long energyPrice, BigInteger value, boolean isAvmCreate, byte[] beaconHash)
        throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {

        SignedTransactionBuilder transactionBuilder = new SignedTransactionBuilder()
                .privateKey(sender.getPrivateKeyBytes())
                .senderNonce(nonce)
                .destination((destination == null) ? null : destination.getAddressBytes())
                .data(data)
                .energyLimit(energyLimit)
                .energyPrice(energyPrice)
                .value(value)
                .beaconHash(beaconHash);

        if (isAvmCreate) {
            transactionBuilder.useAvmTransactionType();
        }

        this.transactionBytes = transactionBuilder.buildSignedTransaction();
    }

    /**
     * Returns a new general transaction. In particular, a general transaction is any transaction
     * that is not an Avm create transaction.
     *
     * @param senderPrivateKey The private key of the sender.
     * @param nonce The nonce of the sender.
     * @param destination The destination address.
     * @param data The data.
     * @param energyLimit The energy limit.
     * @param energyPrice The price per unit energy.
     * @param value The amount to be transferred.
     * @param beaconHash beacon hash (null allowed)
     * @return a new signed transaction.
     */
    public static SignedTransaction newGeneralTransaction(PrivateKey senderPrivateKey,
                                                          BigInteger nonce, Address destination,
                                                          byte[] data, long energyLimit,
                                                          long energyPrice, BigInteger value,
                                                          byte[] beaconHash) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        return new SignedTransaction(senderPrivateKey, nonce, destination, data, energyLimit, energyPrice, value, false, beaconHash);
    }

    /**
     * Returns a new avm create transaction. This transaction attempts to deploy a new Java smart
     * contract.
     *
     * @param senderPrivateKey The private key of the sender.
     * @param nonce The nonce of the sender.
     * @param data The bytes of the jar file to be deployed.
     * @param energyLimit The energy limit.
     * @param energyPrice The price per unit energy.
     * @param value The amount to be transferred to the contract.
     * @param beaconHash beacon hash (null allowed)
     * @return a new signed transaction.
     */
    public static SignedTransaction newAvmCreateTransaction(PrivateKey senderPrivateKey,
                                                            BigInteger nonce, byte[] data,
                                                            long energyLimit, long energyPrice,
                                                            BigInteger value, byte[] beaconHash) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        return new SignedTransaction(senderPrivateKey, nonce, null, data, energyLimit, energyPrice, value, true, beaconHash);
    }

    /**
     * Returns the bytes of the signed transaction.
     *
     * @return The transaction bytes.
     */
    public byte[] getSignedTransactionBytes() {
        return Arrays.copyOf(this.transactionBytes, this.transactionBytes.length);
    }

    /**
     * Returns the hash of this transaction.
     *
     * @return The transaction hash.
     */
    public byte[] getTransactionHash() {
        if (this.hash == null) {
            this.hash = SignedTransactionBuilder.getTransactionHashOfSignedTransaction(this.transactionBytes);
        }
        return this.hash;
    }

    @Override
    public String toString() {
        return "SignedTransaction { hash = " + Hex.encodeHexString(getTransactionHash()) + " }";
    }

}
