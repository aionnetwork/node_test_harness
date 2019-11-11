package org.aion.harness.tests.integ;

import java.math.BigInteger;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.RPC;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.util.conversions.Hex;

/**
 * Utility class for constructing calls to the staker registry contract. Any call that can be made
 * in an eth_call is processed here and returns it's own result. For any consensus-affecting calls,
 * a SignedTransaction object is returned and it must be run from a test that can wait for the
 * transaction to be sealed in a block.
 */
public final class StakerContractUtil {

    private static final byte[] EXPECTED_STAKER_REGISTRY_ADDRESS = Hex.decode("a056337bb14e818f3f53e13ab0d93b6539aa570cba91ce65c716058241989be9");

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    public static BigInteger getTotalStake(Address address) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("getTotalStake")
            .encodeOneAddress(new avm.Address(address.getAddressBytes())).toBytes();

        try {
            Transaction tx = new Transaction(new Address(EXPECTED_STAKER_REGISTRY_ADDRESS), data);
            return new ABIDecoder(rpc.call(tx)).decodeOneBigInteger();
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static avm.Address getCoinbaseAddress(Address address) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("getCoinbaseAddress")
            .encodeOneAddress(new avm.Address(address.getAddressBytes())).toBytes();

        try {
            Transaction tx = new Transaction(new Address(EXPECTED_STAKER_REGISTRY_ADDRESS), data);
            return new ABIDecoder(rpc.call(tx)).decodeOneAddress();
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static void setCoinbaseAddress(Address staker, Address newCoinbaseAddress) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("setCoinbaseAddress")
            .encodeOneAddress(new avm.Address(staker.getAddressBytes()))
            .encodeOneAddress(new avm.Address(newCoinbaseAddress.getAddressBytes())).toBytes();

        try {
            Transaction tx = new Transaction(new Address(EXPECTED_STAKER_REGISTRY_ADDRESS), data);
            rpc.call(tx);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static avm.Address getSigningAddress(Address address) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("getSigningAddress")
            .encodeOneAddress(new avm.Address(address.getAddressBytes())).toBytes();

        try {
            Transaction tx = new Transaction(new Address(EXPECTED_STAKER_REGISTRY_ADDRESS), data);
            return new ABIDecoder(rpc.call(tx)).decodeOneAddress();
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static SignedTransaction getSetSigningAddressTransaction(PreminedAccount account, Address staker, Address newSigningAddress) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("setSigningAddress")
            .encodeOneAddress(new avm.Address(staker.getAddressBytes()))
            .encodeOneAddress(new avm.Address(newSigningAddress.getAddressBytes())).toBytes();

        try {
            // get the nonce over RPC
            BigInteger nonce = rpc.getNonce(account.getAddress()).getResult();

            return SignedTransaction.newGeneralTransaction(
                account.getPrivateKey(),
                nonce,
                new Address(EXPECTED_STAKER_REGISTRY_ADDRESS),
                data,
                2_000_000L,
                10_000_000_000L,
                BigInteger.ZERO,
                null);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static SignedTransaction getSetStateTransaction(PreminedAccount account, Address staker, Boolean newState) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("setState")
            .encodeOneAddress(new avm.Address(staker.getAddressBytes()))
            .encodeOneBoolean(newState).toBytes();

        try {
            // get the nonce over RPC
            BigInteger nonce = rpc.getNonce(account.getAddress()).getResult();

            return SignedTransaction.newGeneralTransaction(
                account.getPrivateKey(),
                nonce,
                new Address(EXPECTED_STAKER_REGISTRY_ADDRESS),
                data,
                2_000_000L,
                10_000_000_000L,
                BigInteger.ZERO,
                null);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static boolean isStakerActive(Address address) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("isActive")
            .encodeOneAddress(new avm.Address(address.getAddressBytes())).toBytes();

        try {
            Transaction tx = new Transaction(new Address(EXPECTED_STAKER_REGISTRY_ADDRESS), data);
            return new ABIDecoder(rpc.call(tx)).decodeOneBoolean();
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static boolean isStaker(Address address) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("isStaker")
            .encodeOneAddress(new avm.Address(address.getAddressBytes())).toBytes();

        try {
            Transaction tx = new Transaction(new Address(EXPECTED_STAKER_REGISTRY_ADDRESS), data);
            return new ABIDecoder(rpc.call(tx)).decodeOneBoolean();
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't decode response");
        }
    }

    public static SignedTransaction getRegisterTransaction(PreminedAccount account, BigInteger amount) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("registerStaker")
            .encodeOneAddress(new avm.Address(account.getAddress().getAddressBytes()))
            .encodeOneAddress(new avm.Address(account.getAddress().getAddressBytes()))
            .encodeOneAddress(new avm.Address(account.getAddress().getAddressBytes())).toBytes();

        try {
            // get the nonce over RPC
            BigInteger nonce = rpc.getNonce(account.getAddress()).getResult();

            return SignedTransaction.newGeneralTransaction(
                account.getPrivateKey(),
                nonce,
                new Address(EXPECTED_STAKER_REGISTRY_ADDRESS),
                data,
                2_000_000L,
                10_000_000_000L,
                amount,
                null);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't encode Transaction");
        }
    }

    public static SignedTransaction getUnbondTransaction(PreminedAccount account, BigInteger amount, BigInteger fee) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("unbond")
            .encodeOneAddress(new avm.Address(account.getAddress().getAddressBytes()))
            .encodeOneBigInteger(amount)
            .encodeOneBigInteger(fee).toBytes();

        try {
            // get the nonce over RPC
            BigInteger nonce = rpc.getNonce(account.getAddress()).getResult();

            return SignedTransaction.newGeneralTransaction(
                account.getPrivateKey(),
                nonce,
                new Address(EXPECTED_STAKER_REGISTRY_ADDRESS),
                data,
                2_000_000L,
                10_000_000_000L,
                BigInteger.ZERO,
                null);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't encode Transaction");
        }
    }

    public static SignedTransaction getUnbondToTransaction(PreminedAccount account, PreminedAccount to, BigInteger amount, BigInteger fee) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("unbondTo")
            .encodeOneAddress(new avm.Address(account.getAddress().getAddressBytes()))
            .encodeOneBigInteger(amount)
            .encodeOneAddress(new avm.Address(to.getAddress().getAddressBytes()))
            .encodeOneBigInteger(fee).toBytes();

        try {
            // get the nonce over RPC
            BigInteger nonce = rpc.getNonce(account.getAddress()).getResult();

            return SignedTransaction.newGeneralTransaction(
                account.getPrivateKey(),
                nonce,
                new Address(EXPECTED_STAKER_REGISTRY_ADDRESS),
                data,
                2_000_000L,
                10_000_000_000L,
                BigInteger.ZERO,
                null);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't encode Transaction");
        }
    }

    public static SignedTransaction getTransferStakeTransaction(PreminedAccount account, PreminedAccount to, BigInteger amount, BigInteger fee) {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("unbondTo")
            .encodeOneAddress(new avm.Address(account.getAddress().getAddressBytes()))
            .encodeOneAddress(new avm.Address(to.getAddress().getAddressBytes()))
            .encodeOneBigInteger(amount)
            .encodeOneBigInteger(fee).toBytes();

        try {
            // get the nonce over RPC
            BigInteger nonce = rpc.getNonce(account.getAddress()).getResult();

            return SignedTransaction.newGeneralTransaction(
                account.getPrivateKey(),
                nonce,
                new Address(EXPECTED_STAKER_REGISTRY_ADDRESS),
                data,
                2_000_000L,
                10_000_000_000L,
                BigInteger.ZERO,
                null);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't encode Transaction");
        }
    }
}
