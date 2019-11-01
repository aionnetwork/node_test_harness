package org.aion.crypto.hash;

// This native function has to be accessed through this class in this exact package, otherwise we
// have to recompile the .so file
public class Blake2bNative {
    public static native byte[] blake256(byte[] in);
}
