package net.rs256.furnace.launcher;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Runs inside the data generator JVM. The bundler starts the real main on its own
 * "ServerMain" thread, so an exception there leaves the JVM exit code at 0;
 * this launcher turns an uncaught exception on any thread into exit code 1.
 *
 * <p>Usage: {@code DatagenLauncher <main class> [args...]}
 */
public final class DatagenLauncher {

    /** Marks the start of the failure report in the datagen log. */
    public static final String FAILURE_MARKER = "[furnace-datagen] uncaught exception in thread ";

    private DatagenLauncher() {}

    public static void main(String[] args) throws Exception {
        // Minecraft replaces System.err with its logger; keep the raw stream.
        final PrintStream err = System.err;
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {
                    synchronized (err) {
                        err.println(FAILURE_MARKER + "\"" + thread.getName() + "\"");
                        throwable.printStackTrace(err);
                        err.flush();
                    }
                    Runtime.getRuntime().halt(1);
                });

        Method main = Class.forName(args[0]).getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) Arrays.copyOfRange(args, 1, args.length));
        } catch (InvocationTargetException e) {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e.getCause());
        }
    }
}
