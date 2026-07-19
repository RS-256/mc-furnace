package net.rs256.furnace;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SIGINT handling: the first signal requests a graceful stop at the
 * next safe boundary (and terminates killable child processes such as the
 * decompiler); second signal exits immediately. Git operations run inside a
 * critical section during which the stop is deferred.
 */
public final class InterruptHandler {

    /** Thrown at a safe boundary once a graceful stop was requested. */
    public static final class AbortException extends RuntimeException {
        public AbortException() {
            super("stop requested");
        }
    }

    private static final AtomicInteger SIGNALS = new AtomicInteger();
    private static volatile boolean stopRequested;
    private static volatile boolean critical;
    private static final Set<Process> KILLABLE = ConcurrentHashMap.newKeySet();

    private InterruptHandler() {}

    public static void install() {
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> onSignal());
        } catch (Throwable t) {
            // Signal API unavailable; graceful stop degrades to default JVM behavior.
        }
    }

    private static void onSignal() {
        if (SIGNALS.incrementAndGet() == 1) {
            stopRequested = true;
            System.err.println();
            System.err.println(
                    "[furnace] stop requested; finishing at a safe boundary (Ctrl+C again to force quit)");
            for (Process p : KILLABLE) {
                p.destroy();
            }
        } else {
            System.err.println();
            System.err.println("[furnace] forced exit; leftover work/ files are cleaned on next start");
            Runtime.getRuntime().halt(130);
        }
    }

    public static boolean stopRequested() {
        return stopRequested;
    }

    /** Throws if a graceful stop was requested and we are not in a critical section. */
    public static void checkAbort() {
        if (stopRequested && !critical) {
            throw new AbortException();
        }
    }

    public static void enterCritical() {
        critical = true;
    }

    public static void exitCritical() {
        critical = false;
        // do not throw here; callers decide when to observe the pending stop
    }

    public static void registerKillable(Process process) {
        KILLABLE.add(process);
        if (stopRequested) {
            process.destroy();
        }
    }

    public static void unregisterKillable(Process process) {
        KILLABLE.remove(process);
    }
}
