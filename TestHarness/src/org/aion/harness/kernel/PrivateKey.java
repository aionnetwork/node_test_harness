package org.aion.harness.kernel;

import org.aion.harness.kernel.utils.CryptoUtils;
import org.apache.commons.codec.binary.Hex;

import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * An Aion private key corresponding to some Aion address.
 *
 * A private key is immutable.
 */
public final class PrivateKey {
    public static final int SIZE = 32;

    private final byte[] privateKeyBytes;
    private final byte[] publicKeyBytes;
    private final Address address;

    /**
     * Constructs a new private key consisting of the provided bytes.
     *
     * @param privateKeyBytes The bytes of the private key.
     */
    private PrivateKey(byte[] privateKeyBytes) throws InvalidKeySpecException {
        if (privateKeyBytes == null) {
            throw new NullPointerException("private key bytes cannot be null");
        }
        if (privateKeyBytes.length != SIZE) {
            throw new IllegalArgumentException("bytes of a private key must have a length of " + SIZE);
        }
        this.privateKeyBytes = copyByteArray(privateKeyBytes);
        this.publicKeyBytes = CryptoUtils.derivePublicKey(this.privateKeyBytes);
        this.address = new Address(CryptoUtils.deriveAddress(this.privateKeyBytes));
    }

    public static PrivateKey fromBytes(byte[] privateKeyBytes) throws InvalidKeySpecException {
        return new PrivateKey(privateKeyBytes);
    }

    public static PrivateKey random() throws InvalidKeySpecException {
        return new PrivateKey(CryptoUtils.generatePrivateKey());
    }

    public Address getAddress() {
        return this.address;
    }

    /**
     * Returns the bytes of this private key.
     *
     * @return The bytes of the private key.
     */
    public byte[] getPrivateKeyBytes() {
        return copyByteArray(this.privateKeyBytes);
    }

    /**
     * Returns the bytes of the public key.
     *
     * @return The bytes of the public key.
     */
    public byte[] getPublicKeyBytes() {
        return copyByteArray(this.publicKeyBytes);
    }

    private static byte[] copyByteArray(byte[] byteArray) {
        return Arrays.copyOf(byteArray, byteArray.length);
    }

    @Override
    public String toString() {
        return "PrivateKey { 0x" + Hex.encodeHexString(this.privateKeyBytes) + " }";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PrivateKey)) {
            return false;
        }
        if (other == this) {
            return true;
        }

        PrivateKey otherPrivateKey = (PrivateKey)other;
        return Arrays.equals(this.privateKeyBytes, otherPrivateKey.getPrivateKeyBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.privateKeyBytes);
    }
}
