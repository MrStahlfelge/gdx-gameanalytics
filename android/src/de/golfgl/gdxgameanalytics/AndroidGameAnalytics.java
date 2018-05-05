package de.golfgl.gdxgameanalytics;

import android.os.Build;

/**
 * Use this subclass on Android to set platform, version, device and manufacturer automatically
 * <p>
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class AndroidGameAnalytics extends de.golfgl.gdxgameanalytics.GameAnalytics {
    @Override
    public void startSession() {
        setPlatform(Platform.Android);
        setPlatformVersionString(Build.VERSION.RELEASE);
        setDevice(Build.DEVICE);
        setManufacturer(Build.MANUFACTURER);

        super.startSession();
    }
}
