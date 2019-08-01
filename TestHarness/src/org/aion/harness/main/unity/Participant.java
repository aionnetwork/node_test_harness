package org.aion.harness.main.unity;

import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.TransactionResult;

import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;

public class Participant {
    
    private static final long ENERGY_LIMIT_CALL = 1_233_567L;
    private static final long ENERGY_PRICE = 10_000_000_000L;

    private final PrivateKey privateKey;
    private final Address participantAddress;
    private final Address stakingRegistry;
    private final RPC rpc;

    public Participant(Address registryAddress) throws InvalidKeySpecException {
        this(PrivateKey.random(), registryAddress, new RPC("127.0.0.1", "8545"));
    }

    public Participant(Address registryAddress, RPC rpc) throws InvalidKeySpecException {
        this(PrivateKey.random(), registryAddress, rpc);
    }

    public Participant(PrivateKey privateKey, Address registryAddress, RPC rpc) {
        this.privateKey = privateKey;
        this.participantAddress = privateKey.getAddress();
        this.stakingRegistry = registryAddress;
        this.rpc = rpc;
    }
    
    public RawTransaction getRegisterTransaction() throws InterruptedException {
        // encode a register tx
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("register").encodeOneAddress(new avm.Address(participantAddress.getAddressBytes())).toBytes();

        // get the nonce over RPC
        BigInteger nonce = rpc.getNonce(participantAddress).getResult();

        TransactionResult txresult = RawTransaction.buildAndSignGeneralTransaction(privateKey, nonce, stakingRegistry, data, ENERGY_LIMIT_CALL, ENERGY_PRICE, BigInteger.ZERO);
        
        return txresult.getTransaction();
    }

    public TransactionReceipt register() throws InterruptedException {
        ReceiptHash hash = rpc.sendTransaction(getRegisterTransaction()).getResult();
        return rpc.getTransactionReceipt(hash).getResult();
    }

    public RawTransaction getVoteTransaction(Address address, BigInteger vote) throws InterruptedException {

        // encode a vote tx
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("vote").encodeOneAddress(new avm.Address(address.getAddressBytes())).toBytes();

        // get the nonce over RPC
        BigInteger nonce = rpc.getNonce(participantAddress).getResult();

        TransactionResult txresult = RawTransaction.buildAndSignGeneralTransaction(privateKey, nonce, stakingRegistry, data, ENERGY_LIMIT_CALL, ENERGY_PRICE, vote);        
        
        return txresult.getTransaction();
    }

    public TransactionReceipt vote(Address address, BigInteger vote) throws InterruptedException {
        ReceiptHash hash = rpc.sendTransaction(getVoteTransaction(address, vote)).getResult();
        return rpc.getTransactionReceipt(hash).getResult();
    }

    public RawTransaction getUnvoteTransaction(Address address, long amountToUnvote) throws InterruptedException {

        // encode an unvote tx
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("unvote")
                .encodeOneAddress(new avm.Address(address.getAddressBytes()))
                .encodeOneLong(amountToUnvote).toBytes();

        // get the nonce over RPC
        BigInteger nonce = rpc.getNonce(participantAddress).getResult();

        TransactionResult txresult = RawTransaction.buildAndSignGeneralTransaction(privateKey, nonce, stakingRegistry, data, ENERGY_LIMIT_CALL, ENERGY_PRICE, BigInteger.ZERO);
        
        return txresult.getTransaction();
    }

    public TransactionReceipt unvote(Address address, long amountToUnvote) throws InterruptedException {
        ReceiptHash hash = rpc.sendTransaction(getUnvoteTransaction(address, amountToUnvote)).getResult();
        return rpc.getTransactionReceipt(hash).getResult();
    }

    public long getVote() throws InterruptedException {

        // encode a getVote tx
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString("getVote")
                .encodeOneAddress(new avm.Address(participantAddress.getAddressBytes())).toBytes();

        // get the nonce over RPC
        BigInteger nonce = rpc.getNonce(participantAddress).getResult();

        Transaction tx = new Transaction(stakingRegistry, data);

        // send it using RPC
        byte[] result = rpc.call(tx);
        return new ABIDecoder(result).decodeOneLong();
    }

    public Address getParticipantAddress() {
        return participantAddress;
    }
}
