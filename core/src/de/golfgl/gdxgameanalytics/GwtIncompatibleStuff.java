package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.SharedLibraryLoader;

import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class GwtIncompatibleStuff {

    protected static String generateHash(String json, String secretKey) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            byte[] encoded = secretKey.getBytes();
            SecretKeySpec secretKeySpec = new SecretKeySpec(encoded, "HmacSHA256");
            sha256_HMAC.init(secretKeySpec);
            return new String(Base64Coder.encode(sha256_HMAC.doFinal(json.getBytes())));
        } catch (Exception ex) {
            Gdx.app.error(GameAnalytics.TAG, "Error generating Hmac: " + ex.toString());
            return "";
        }
    }

    protected static String generateUuid()  {
        UUID sid = UUID.randomUUID();
        return sid.toString();
    }

    protected static GameAnalytics.Platform getDefaultPlatform(Application.ApplicationType type) {
        switch (type) {
            case Android:
                return GameAnalytics.Platform.Android;
            case WebGL:
                return GameAnalytics.Platform.WebGL;
            default:
                if (SharedLibraryLoader.isWindows)
                    return GameAnalytics.Platform.Windows;
                else if (SharedLibraryLoader.isLinux)
                    return GameAnalytics.Platform.Linux;
                else if (SharedLibraryLoader.isIos)
                    return GameAnalytics.Platform.iOS;
                else if (SharedLibraryLoader.isMac)
                    return GameAnalytics.Platform.MacOS;
                else
                    throw new IllegalStateException("You need to set a platform");
        }
    }
}
