package net.rs256.furnace.pipe;

/**
 * A version that cannot be ingested by design (e.g. obfuscated jars without
 * published mappings: 19w34a/19w35a). Batches log it and continue.
 */
public class SkipVersionException extends RuntimeException {

    public SkipVersionException(String message) {
        super(message);
    }
}
