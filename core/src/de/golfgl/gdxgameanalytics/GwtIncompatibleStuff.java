package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.SharedLibraryLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class GwtIncompatibleStuff {

    private static String generateHash(byte[] json, String secretKey) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            byte[] encoded = secretKey.getBytes();
            SecretKeySpec secretKeySpec = new SecretKeySpec(encoded, "HmacSHA256");
            sha256_HMAC.init(secretKeySpec);
            return new String(Base64Coder.encode(sha256_HMAC.doFinal(json)));
        } catch (Exception ex) {
            Gdx.app.error(GameAnalytics.TAG, "Error generating Hmac: " + ex.toString());
            return "";
        }
    }

    /**
     * @return UUID on Java, a nearly-UUID on GWT
     */
    public static String generateUuid() {
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

    /**
     * sets the http request content, zipped if possible.
     *
     * @return header for authentication
     */
    public static String setHttpRequestContent(Net.HttpRequest request, String content, String secretKey) {
        byte[] compressedContent = null;
        String hash;

        try {
            compressedContent = compress(content);
        } catch (Throwable t) {
            // do nothing
        }

        Gdx.app.debug(GameAnalytics.TAG, content);

        if (compressedContent != null) {
            Gdx.app.debug(GameAnalytics.TAG, "(Compressed from " + content.length() +
                    " to " + compressedContent.length + " bytes)");

            request.setContent(new ByteArrayInputStream(compressedContent), compressedContent.length);
            hash = GwtIncompatibleStuff.generateHash(compressedContent, secretKey);
            request.setHeader("Content-Encoding", "gzip");
        } else {
            hash = GwtIncompatibleStuff.generateHash(content.getBytes(), secretKey);
            request.setContent(content);
        }
        return hash;
    }

    public static byte[] compress(String paramString) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(paramString.length());
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write(paramString.getBytes());
        gzipOutputStream.close();
        byte[] bytes = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        return bytes;
    }
}
