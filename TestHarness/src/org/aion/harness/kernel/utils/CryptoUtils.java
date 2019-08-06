package org.aion.harness.kernel.utils;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import net.i2p.crypto.eddsa.Utils;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class CryptoUtils {
    private static final String skEncodedPrefix = "302e020100300506032b657004220420";
    private static final String pkEncodedPrefix = "302a300506032b6570032100";
    private static final EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    public static byte[] generatePrivateKey() {
        KeyPairGenerator keyPairGenerator = new KeyPairGenerator();
        KeyPair pair = keyPairGenerator.generateKeyPair();
        EdDSAPrivateKey privateKey = (EdDSAPrivateKey) pair.getPrivate();
        return Utils.hexToBytes(Utils.bytesToHex(privateKey.getEncoded()).substring(32, 96));
    }

    /**
     * Derive the corresponding aion address, given the private key bytes.
     */
    public static byte[] deriveAddress(byte[] privateKeyBytes) throws InvalidKeySpecException {
        return computeA0Address(derivePublicKey(privateKeyBytes));
    }

    /**
     * Derive the corresponding aion address, given the private key bytes.
     */
    public static byte[] derivePublicKey(byte[] privateKeyBytes) throws InvalidKeySpecException {
        if (privateKeyBytes == null) {
            throw new NullPointerException("private key cannot be null");
        }

        if (privateKeyBytes.length != 32){
            throw new IllegalArgumentException("private key mute be 32 bytes");
        }

        EdDSAPrivateKey privateKey = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(addSkPrefix(Utils.bytesToHex(privateKeyBytes))));
        return privateKey.getAbyte();
    }

    /**
     * Add encoding prefix for importing public key
     */
    private static byte[] addPkPrefix(String pkString){
        String pkEncoded = pkEncodedPrefix + pkString;
        return Utils.hexToBytes(pkEncoded);
    }

    /**
     * Add encoding prefix for importing private key
     */
    private static byte[] addSkPrefix(String skString){
        String skEncoded = skEncodedPrefix + skString;
        return Utils.hexToBytes(skEncoded);
    }

    private static byte[] computeA0Address(byte[] publicKey) {
        byte A0_IDENTIFIER = (byte) 0xa0;
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(A0_IDENTIFIER);
        buf.put(blake256(publicKey), 1, 31);
        return buf.array();
    }

    private static byte[] blake256(byte[] input) {
        Blake2b digest = Blake2b.Digest.newInstance(32);
        digest.update(input);
        return digest.digest();
    }
}
