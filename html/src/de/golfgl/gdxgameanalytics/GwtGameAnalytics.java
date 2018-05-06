package de.golfgl.gdxgameanalytics;

/**
 * Use this subclass on Android to set platform, version, device and manufacturer automatically
 * Created by Benjamin Schulte on 06.05.2018.
 */

public class GwtGameAnalytics extends GameAnalytics {
    @Override
    public void startSession() {
        setPlatform(Platform.WebGL);
        setPlatformVersionString("0");

        //String browserType = getBrowserType();
        setDevice("Webbrowser");

        super.startSession();
    }

//    protected String getBrowserType() {
//        String browserType;
//        String userAgent = Window.Navigator.getUserAgent();
//        if (userAgent == null) {
//            browserType = "Webbrowser";
//        } else if (userAgent.indexOf("Chrome/") != -1) {
//            browserType = "Chrome";
//        } else if (userAgent.indexOf("Safari/") != -1) {
//            browserType = "Safari";
//        } else if (userAgent.indexOf("Firefox/") != -1 || userAgent.indexOf("Minefield/") != -1) {
//            browserType = "Firefox";
//        } else {
//            browserType = "Webbrowser";
//        }
//
//        return browserType;
//    }
}
