package de.golfgl.gdxgameanalytics;

import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Use this subclass on Android to set platform, version, device and manufacturer automatically
 * <p>
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class AndroidGameAnalytics extends GameAnalytics {
    private Thread.UncaughtExceptionHandler androidUncaughtExceptionHandler;

    @Override
    public void startSession() {
        setPlatform(Platform.Android);
        if (!setPlatformVersionString(Build.VERSION.RELEASE)) {
            // just in case we have a very strange Android version
            setPlatformVersionString("0");
        }

        setDevice(Build.MODEL);
        setManufacturer(Build.MANUFACTURER);

        super.startSession();
    }

    /**
     * Registers a handler for catching all uncaught exceptions to send them to GA.
     */
    public void registerUncaughtExceptionHandler() {

        // don't register twice
        if (androidUncaughtExceptionHandler != null)
            return;

        androidUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String exceptionAsString = sw.toString();

                    submitErrorEvent(ErrorType.error, exceptionAsString);
                    flushQueueImmediately();

                    int waitTime = 0;
                    // let's wait a second in case sending is slow
                    while (flushingQueue && waitTime < 10) {
                        Thread.sleep(100);
                        waitTime++;
                    }

                } catch (Throwable ignore) {
                    // ignore
                } finally {
                    // Let Android show the default error dialog
                    androidUncaughtExceptionHandler.uncaughtException(t, e);
                }
            }
        });
    }

    public void unregisterUncaughtExceptionHandler() {
        if (androidUncaughtExceptionHandler == null)
            return;

        Thread.setDefaultUncaughtExceptionHandler(androidUncaughtExceptionHandler);
        androidUncaughtExceptionHandler = null;
    }
}
