package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Net;

/**
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class GwtIncompatibleStuff {

    // https://github.com/bitwiseshiftleft/sjcl/
    private native static String generateHash(String json, String secretKey) /*-{
        hmac = new $wnd.sjcl.misc.hmac($wnd.sjcl.codec.utf8String.toBits(secretKey), $wnd.sjcl.hash.sha256);
        return $wnd.sjcl.codec.base64.fromBits(hmac.encrypt(json));
    }-*/;

    protected native static String generateUuid() /*-{
     function s4() {
        return $wnd.Math.floor((1 + $wnd.Math.random()) * 0x10000)
          .toString(16)
         .substring(1);
     }
     return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
    }-*/;

    protected static GameAnalytics.Platform getDefaultPlatform(Application.ApplicationType type) {
        return GameAnalytics.Platform.WebGL;
    }

    protected static String setHttpRequestContent(Net.HttpRequest request, String content, String secretKey) {
        String hash = GwtIncompatibleStuff.generateHash(content, secretKey);
        request.setContent(content);
        return hash;
    }
}
