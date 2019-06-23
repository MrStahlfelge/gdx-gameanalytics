package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.Gdx;

public class DesktopGameAnalytics extends GameAnalytics {

    @Override
    public void startSession() {
        this.setPlatform(GwtIncompatibleStuff.getDefaultPlatform(Gdx.app.getType()));
        this.setPlatformVersionString("0");
        super.startSession();
    }

    @Override
    public void closeSession() {
        super.closeSession();

        //Spawn thread to keep application running when closing and need to flush data.
        new Thread() {
            @Override
            public void run() {
                int waitTime = 0;
                while (flushingQueue && waitTime < 30) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        // do nothing
                    }
                    waitTime++;
                }
            }
        }.start();
    }

    /**
     * Registers a handler for catching all uncaught exceptions to send them to GA. Exits the app afterwards
     */
    public void registerUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    sendThrowableAsErrorEventSync(e);
                } catch (Throwable throwable) {
                    // do nothing
                } finally {
                    e.printStackTrace(System.err);
                }
            }
        });
    }
}
