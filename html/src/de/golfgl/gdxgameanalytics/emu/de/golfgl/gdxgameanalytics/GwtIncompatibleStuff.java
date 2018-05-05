package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.Application;

/**
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class GwtIncompatibleStuff {

    protected static String generateHash(String json, String secretKey) {
        return "";
    }

    protected static String generateUuid() {
        return "";
    }

    protected static GameAnalytics.Platform getDefaultPlatform(Application.ApplicationType type) {
        return GameAnalytics.Platform.WebGL;
    }

}
