package org.aion.equihash;

/**
 * This package contains utility functions commonly called across multiple equihash classes.
 *
 * @author Ross (ross@nuco.io)
 */
public class EquiUtils {

    /**
     * Converts a solution to its compress I2BSP format to be added to block headers.
     *
     * @param indices Indices array
     * @param cBitLen Collision bit length
     * @return Minimized version of passed indices
     * @throws NullPointerException when given null input
     */
    public static byte[] getMinimalFromIndices(int[] indices, int cBitLen) {

        if (indices == null) throw new NullPointerException("null indices array");

        int lenIndices = indices.length * Integer.BYTES;
        int minLen = (cBitLen + 1) * lenIndices / (8 * Integer.BYTES);
        int bytePad = Integer.BYTES - ((cBitLen + 1) + 7) / 8;

        byte[] arr = intsToBytesBigEndian(indices);

        byte[] ret = new byte[minLen];
        compressArray(arr, ret, cBitLen + 1, bytePad);

        return ret;
    }

    /**
     * Compress an array into index format.
     *
     * @param in Input array
     * @param out Output array
     * @param bitLen Number of bits to compress
     * @param bytePad Byte padding to ensure a whole number of bytes examined.
     * @throws NullPointerException when given null input
     */
    private static void compressArray(byte[] in, byte[] out, int bitLen, int bytePad) {

        if (in == null) throw new NullPointerException("null input array");
        else if (out == null) throw new NullPointerException("null output array");

        int inWidth = (bitLen + 7) / 8 + bytePad;
        int bitLenMask = (1 << bitLen) - 1;

        int accBits = 0;
        int accValue = 0;

        int j = 0;
        for (int i = 0; i < out.length; i++) {
            if (accBits < 8) {
                accValue = accValue << bitLen;
                for (int x = bytePad; x < inWidth; x++) {
                    accValue =
                            accValue
                                    | ((in[j + x]
                                                    & ((bitLenMask >> (8 * (inWidth - x - 1)))
                                                            & 0xFF))
                                            << (8 * (inWidth - x - 1)));
                }
                j += inWidth;
                accBits += bitLen;
            }

            accBits -= 8;
            out[i] = (byte) ((accValue >> accBits) & 0xFF);
        }
    }

    private static byte[] intsToBytesBigEndian(int[] arr) {
        byte[] ret = new byte[arr.length * 4];
        intsToBytesBigEndian(arr, ret);
        return ret;
    }

    private static void intsToBytesBigEndian(int[] arr, byte[] b) {
        int off = 0;
        for (int ii : arr) {
            b[off++] = (byte) (ii >>> 24);
            b[off++] = (byte) (ii >>> 16);
            b[off++] = (byte) (ii >>> 8);
            b[off++] = (byte) ii;
        }
    }
}
