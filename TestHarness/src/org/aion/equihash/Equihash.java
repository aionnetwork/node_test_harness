package org.aion.equihash;

import static org.aion.crypto.hash.Blake2bNative.blake256;

import java.io.File;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.aion.harness.main.types.BlockTemplate;
import org.aion.harness.main.types.MinedBlockSolution;

/**
 * This class serves as the front end interface to the Tromp Equihash solver accessed through JNI.
 * This class also contains methods to verify equihash solutions, either generated locally or
 * received from peers.
 */
public class Equihash {
    private int cBitLen; // Collision Bit Length used by equihash
    AtomicLong totalSolGenerated;

    /*
     * Load native libraries
     */
    static {
        System.load(new File("native/libsodium.so").getAbsolutePath());
        System.load(new File("native/libblake2b.so").getAbsolutePath());
        System.load(new File("native/equiMiner.so").getAbsolutePath());
    }

    // This native function has to be accessed through this class in this exact package, otherwise we
    // have to recompile the .so file
    public native int[][] solve(byte[] nonce, byte[] headerBytes);

    /**
     * Create a new Equihash instance with the parameters (n,k)
     *
     * @param n Total number of bits over which to do XOR collisions
     * @param k Number of steps with which to solve.
     */
    public Equihash(int n, int k) {
        this.cBitLen = n / (k + 1);
        this.totalSolGenerated = new AtomicLong(0);
    }

    /**
     * Retrieves a set of possible solutions given the passed header and nonce value Any number of
     * solutions may be returned; the maximum number of solutions observed has been 8
     *
     * @param header A 32 byte hash of the block header (minus nonce and solutions)
     * @param nonce - A 32 byte header
     * @return An array of equihash solutions
     */
    private int[][] getSolutionsForNonce(byte[] header, byte[] nonce) {

        int[][] solutions = new int[0][0];

        if (header != null && header.length == 32 && nonce != null && nonce.length == 32) {
            // Call JNI to retrieve a solution
            solutions = this.solve(nonce, header);
        }
        return solutions;
    }

    /*
     * Mine for a single nonce
     */
    public MinedBlockSolution mine(BlockTemplate blockTemplate, byte[] nonce) {

        byte[] headerHash = blockTemplate.getHeaderHash();

        // Convert byte to LE order (in place)
        toLittleEndianByteArray(nonce);

        // Get solutions for this nonce
        int[][] generatedSolutions = getSolutionsForNonce(headerHash, nonce);

        // Increment number of solutions
        this.totalSolGenerated.addAndGet(generatedSolutions.length);

        // Add nonce and solutions, hash and check if less than target

        // Check each returned solution
        for (int[] generatedSolution : generatedSolutions) {

            // Verify if any of the solutions pass the difficulty filter, return if true.
            byte[] minimal = EquiUtils.getMinimalFromIndices(generatedSolution, cBitLen);

            byte[] validationBytes = merge(headerHash, nonce, minimal);

            // Found a valid solution
            if (isValidBlock(validationBytes, blockTemplate.difficultyTarget)) {
                return new MinedBlockSolution(headerHash, nonce, minimal);
            }
        }

        return null;
    }

    /**
     * Checks if the solution meets difficulty requirements for this block.
     *
     * @param target Target under which hash must fall below
     * @return True is the solution meets target conditions; false otherwise.
     */
    private boolean isValidBlock(byte[] validationBytes, BigInteger target) {

        // Default blake2b without personalization to test if hash is below
        // difficulty
        BigInteger hdrDigest = new BigInteger(1, blake256(validationBytes));

        return (hdrDigest.compareTo(target) < 0);
    }


    /** Perform an in-place conversion of a byte array from big endian to little endian. */
    private static void toLittleEndianByteArray(byte[] toConvert) {

        if (toConvert == null) { return; }

        for (int i = 0; i < (toConvert.length >>> 1); i++) {
            byte temp = toConvert[i];
            toConvert[i] = toConvert[toConvert.length - 1 - i];
            toConvert[toConvert.length - 1 - i] = temp;
        }
    }

    /**
     * @param arrays - arrays to merge
     * @return - merged array
     */
    private static byte[] merge(byte[]... arrays) {
        int count = 0;
        for (byte[] array : arrays) {
            count += array.length;
        }

        // Create new array and copy all array contents
        byte[] mergedArray = new byte[count];
        int start = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, mergedArray, start, array.length);
            start += array.length;
        }
        return mergedArray;
    }
}
