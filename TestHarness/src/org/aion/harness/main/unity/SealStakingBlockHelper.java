package org.aion.harness.main.unity;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.Utils;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.main.RPC;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public class SealStakingBlockHelper {
    private final PrivateKey privateKey;
    private final RPC rpc;

    public SealStakingBlockHelper(PrivateKey _privateKey, RPC _rpc) {
        privateKey = _privateKey;
        rpc = _rpc;
    }

    public boolean sealBlock()
            throws InterruptedException, InvalidKeySpecException, NoSuchAlgorithmException,
                    InvalidKeyException, SignatureException {

        byte[] seed = getSeed();
        if (seed == null) {
            return false;
        }

        EdDSAPrivateKey edDSAkey =
                new EdDSAPrivateKey(
                        new PKCS8EncodedKeySpec(addSkPrefix(privateKey.getPrivateKeyBytes())));

        byte[] sealingHash = submitSeed(seed, edDSAkey);
        if (sealingHash == null) {
            return false;
        }

        return submitSignature(sealingHash, edDSAkey);
    }

    private boolean submitSignature(byte[] sealingHash, EdDSAPrivateKey edDSAkey)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException,
                    InterruptedException {

        byte[] signature = sign(edDSAkey, sealingHash);

        String submitSignaturePayload =
                String.format(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"submitsignature\",\"params\":[\"0x%s\", \"0x%s\"],\"id\":1}",
                        Hex.encodeHexString(signature), Hex.encodeHexString(sealingHash));

        return Boolean.parseBoolean(rpc.call(submitSignaturePayload));
    }

    private byte[] submitSeed(byte[] seed, EdDSAPrivateKey edDSAkey)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException,
                    InterruptedException {

        byte[] newSeed = sign(edDSAkey, seed);

        String submitSeedPayload =
                String.format(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"submitseed\",\"params\":[\"0x%s\", \"0x%s\"],\"id\":1}",
                        Hex.encodeHexString(newSeed), Hex.encodeHexString(edDSAkey.getAbyte()));

        String result = rpc.call(submitSeedPayload);
        if (result.equals("") || result.equals("0x")) {
            return null;
        }

        byte[] sealingHash;
        try {
            String resultHex = result.replace("0x", "");
            sealingHash = Hex.decodeHex(resultHex);
        } catch (DecoderException dx) {
            throw new IllegalStateException(
                    "The submit result from kernel could not be hex decoded.  result was:"
                            + result);
        }

        return sealingHash;
    }

    private byte[] getSeed() throws InterruptedException {
        String getseedPayload =
                "{\"jsonrpc\":\"2.0\",\"method\":\"getseed\",\"params\":[],\"id\":1}";

        String result = rpc.call(getseedPayload);
        if (result.equals("") || result.equals("0x")) {
            return null;
        }

        byte[] seed;
        try {
            String resultHex = result.replace("0x", "");
            seed = Hex.decodeHex(resultHex);
        } catch (DecoderException dx) {
            throw new IllegalStateException(
                    "The getseed result from kernel could not be hex decoded.  result was:"
                            + result);
        }
        return seed;
    }

    private static byte[] addSkPrefix(byte[] skString) {
        byte[] skEncoded = Utils.hexToBytes("302e020100300506032b657004220420");
        byte[] encoded = Arrays.copyOf(skEncoded, skEncoded.length + skString.length);
        System.arraycopy(skString, 0, encoded, skEncoded.length, skString.length);
        return encoded;
    }

    private static byte[] sign(EdDSAPrivateKey privateKey, byte[] data)
            throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName("Ed25519");
        EdDSAEngine edDSAEngine =
                new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        edDSAEngine.initSign(privateKey);
        return edDSAEngine.signOneShot(data);
    }
}
