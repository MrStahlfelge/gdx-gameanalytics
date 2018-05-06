package de.golfgl.gdxgameanalytics;

import android.os.Build;

/**
 * Use this subclass on Android to set platform, version, device and manufacturer automatically
 * <p>
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class AndroidGameAnalytics extends GameAnalytics {
    @Override
    public void startSession() {
        setPlatform(Platform.Android);
        if (!setPlatformVersionString(Build.VERSION.RELEASE)) {
            // just in case we have a very strange Android version
            setPlatformVersionString("0");
        }

        // why this? Two reasons: Devices are limited to 500 unique values and there are tenthousands of devices
        // around. And you can only filter by device, not by manufacturer while the latter is more interesting
        setDevice(Build.MANUFACTURER);
        setManufacturer(Build.MANUFACTURER);

        super.startSession();
    }
}
