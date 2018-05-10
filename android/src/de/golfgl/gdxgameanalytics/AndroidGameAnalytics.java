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

        setDevice(Build.MODEL);
        setManufacturer(Build.MANUFACTURER);

        super.startSession();
    }
}
