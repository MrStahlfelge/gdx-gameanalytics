package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.backends.iosrobovm.custom.HWMachine;

import org.robovm.apple.foundation.NSDictionary;
import org.robovm.apple.foundation.NSException;
import org.robovm.apple.uikit.UIDevice;

public class IosGameAnalytics extends GameAnalytics {

    @Override
    public void startSession() {
        this.setPlatform(Platform.iOS);
        UIDevice currentDevice = UIDevice.getCurrentDevice();
        if (!this.setPlatformVersionString(currentDevice.getSystemVersion())) {
            this.setPlatformVersionString("0");
        }

        this.setDevice(HWMachine.getMachineString());
        this.setManufacturer("Apple");
        super.startSession();
    }

    /**
     * Registers a handler for catching all uncaught exceptions to send them to GA. Exits the app afterwards
     */
    public void registerUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                String exceptionAsString = null;
                try {
                    exceptionAsString = sendThrowableAsErrorEventSync(e);
                } catch (Throwable throwable) {
                    // do nothing
                } finally {
                    // crash the app in iOS
                    NSException exception = new NSException(e.getClass().getName(), exceptionAsString != null ? exceptionAsString
                            : "(no stacktrace)", new NSDictionary());
                    exception.raise();
                }

            }
        });
    }

}
