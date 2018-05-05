import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gameanalytics.com client for libGDX
 * <p>
 * Created by Benjamin Schulte on 05.05.2018 based up on example implementation
 * https://s3.amazonaws.com/download.gameanalytics.com/examples/GameAnalytics+REST+API+example.java
 * <p>
 * (That's the reason why this code looks like made by a server dev - it acutally is. Improvements welcome)
 */

public class GameAnalytics {
    protected static final String URL_SANDBOX = "http://sandbox-api.gameanalytics.com/v2/";
    private static final String TAG = "Gameanalytics";
    private final static String sdk_version = "rest api v2";
    private static final int FLUSH_QUEUE_INTERVAL = 20;
    private static final String URL_GAMEANALYTICS = "https://api.gameanalytics.com/v2/";
    protected Timer.Task pingTask;

    protected String url = URL_GAMEANALYTICS;
    private String game_key = null;
    private String secret_key = null;

    //dimension information
    private String platform = null;
    private String os_version = null;
    private String device = "unknown";
    private String manufacturer = "unkown";
    //game information
    private String build;
    //user information
    private String user_id = null;
    private String session_id;
    private int session_num = 0;
    private String custom1;
    private String custom2;
    private String custom3;

    //SDK status
    private boolean canSendEvents = false;
    private int lastFailedWait = 0;
    //TODO maximum queue size
    private List<AnnotatedEvent> queue = new ArrayList<AnnotatedEvent>();
    private boolean flushingQueue;
    private long timeStampDiscrepancy;
    private Preferences prefs;

    private static String generateHash(String json, String secretKey) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            byte[] encoded = secretKey.getBytes();
            SecretKeySpec secretKeySpec = new SecretKeySpec(encoded, "HmacSHA256");
            sha256_HMAC.init(secretKeySpec);
            return new String(Base64Coder.encode(sha256_HMAC.doFinal(json.getBytes())));
        } catch (Exception ex) {
            Gdx.app.error(TAG, "Error generating Hmac: " + ex.toString());
            return "";
        }
    }

    /**
     * initializes the session. Make sure you have set all neccessary parameters before calling this
     */
    public void initSession() {
        if (game_key == null || secret_key == null)
            throw new IllegalStateException("You must set your game key and secret key");

        if (platform == null) {
            Application.ApplicationType type = Gdx.app.getType();
            switch (type) {
                case Android:
                    setPlatform(Platform.Android);
                case WebGL:
                    setPlatform(Platform.WebGL);
                default:
                    throw new IllegalStateException("You need to set a platform");
            }

        }
        if (os_version == null)
            throw new IllegalStateException("You need to set a os version");

        if (!os_version.startsWith(platform)) {
            os_version = platform + " " + os_version;
        }

        if (prefs == null)
            Gdx.app.log(TAG, "You did not set up preferences. Session and user tracking will not work without it");

        loadOrInitUserStringAndSessionNum();

        session_id = generateSessionID();
        createInitRequest();
        createUserEvent();
    }

    private void loadOrInitUserStringAndSessionNum() {
        if (prefs != null) {
            user_id = prefs.getString("ga_userid", null);
            session_num = prefs.getInteger("ga_sessionnum", 0);
        }

        if (user_id == null) {
            Gdx.app.log(TAG, "No user id found. Generating a new one");
            user_id = UUID.randomUUID().toString();

            if (prefs != null)
                prefs.putString("ga_userid", user_id);
        }

        session_num++;

        if (prefs != null) {
            prefs.putInteger("ga_sessionnum", session_num);
            prefs.flush();
        }
    }

    protected void flushQueue() {
        if (!canSendEvents || flushingQueue)
            return;

        if (lastFailedWait > 0) {
            lastFailedWait -= 1;
            return;
        }

        flushingQueue = true;

        String payload = "[";
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        synchronized (queue) {
            for (int i = 0; i < queue.size(); i++) {
                if (i != queue.size() - 1) {
                    payload += json.toJson(queue.get(i)) + ",\n";
                } else {
                    payload += json.toJson(queue.get(i)) + "\n";
                }
            }
        }
        payload += "]";

        final Net.HttpRequest request = new Net.HttpRequest("POST");
        request.setUrl(url + game_key + "/events");
        request.setContent(payload);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-type", "application/json");

        // authorization
        String hash = generateHash(payload, secret_key);
        request.setHeader("Authorization", hash);

        //Execute and read response
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                synchronized (queue) {
                    queue.clear();
                }

                int statusCode = httpResponse.getStatus().getStatusCode();
                String resultAsString = httpResponse.getResultAsString();

                if (statusCode == 200)
                    Gdx.app.debug(TAG, request.getContent() + "\n" + statusCode + " " + resultAsString);
                else
                    Gdx.app.error(TAG, "Could not submit events: " + statusCode + " " + resultAsString);

                flushingQueue = false;
            }

            @Override
            public void failed(Throwable t) {
                failed();
            }

            @Override
            public void cancelled() {
                failed();
            }

            private void failed() {
                Gdx.app.error(TAG, "could not send events in queue.");
                addLastFailed();
                flushingQueue = false;
            }
        });
    }

    private void addLastFailed() {
        lastFailedWait = Math.max(lastFailedWait * 2, 2);
        lastFailedWait = Math.min(15, lastFailedWait);
    }

    private String generateSessionID() {
        UUID sid = UUID.randomUUID();
        return sid.toString();
    }

    private void createUserEvent() {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "user");
        synchronized (queue) {
            queue.add(event);
        }
    }

    public void submitDesignEvent(String event_id) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "design");
        event.put("event_id", event_id);
        synchronized (queue) {
            queue.add(event);
        }
    }

    public void submitDesignEvent(String event_id, float amount) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "design");
        event.put("event_id", event_id);
        event.putFloat("amount", amount);
        synchronized (queue) {
            queue.add(event);
        }
    }

    private void createGenericBusinessEvent(String event_id, int amount, String currency, int transaction_num) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "business");
        event.put("event_id", event_id);
        event.putInt("amount", amount);
        event.put("currency", currency);
        event.putInt("transaction_num", transaction_num);
        synchronized (queue) {
            queue.add(event);
        }
    }

    private void createGenericBusinessEventGoogle(String event_id, int amount, String currency, int
            transaction_num, String cart_type, String receipt, String signature) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "business");
        event.put("event_id", event_id);
        event.putInt("amount", amount);
        event.put("currency", currency);
        event.putInt("transaction_num", transaction_num);
        event.put("cart_type", cart_type);

        // FIXME createGenericBusinessEventGoogle
        //receipt
        //JSONObject receipt_info = new JSONObject();
        //receipt_info.put("receipt", receipt);
        //receipt_info.put("signature", signature);
        //event.put("receipt_info", receipt_info);
        synchronized (queue) {
            queue.add(event);
        }
    }

    public void submitProgressionEvent(ProgressionStatus status, String progression01, String progression02,
                                       String progression03) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "progression");

        String event_id = status.toString() + ":" + progression01;
        if (progression02.length() > 0) {
            event_id += ":" + progression02;
        }
        if (progression03.length() > 0) {
            event_id += ":" + progression03;
        }
        event.put("event_id", event_id);

        if (status == ProgressionStatus.Complete || status == ProgressionStatus.Fail) {
            int attempt_num = 1; //increment each time this particular progression event has been generated with
            // status fail.
            event.putInt("attempt_num", attempt_num);
        }
        synchronized (queue) {
            queue.add(event);
        }
    }

    public void submitProgressionEvent(ProgressionStatus status, String progression01, String progression02,
                                       String progression03, int score) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "progression");

        String event_id = status.toString() + ":" + progression01;
        if (progression02.length() > 0) {
            event_id += ":" + progression02;
        }
        if (progression03.length() > 0) {
            event_id += ":" + progression03;
        }
        event.put("event_id", event_id);

        if (status == ProgressionStatus.Complete || status == ProgressionStatus.Fail) {
            int attempt_num = 1; //increment each time this particular progression event has been generated with
            // status fail.
            event.putInt("attempt_num", attempt_num);
            event.putInt("score", score);
        }
        synchronized (queue) {
            queue.add(event);
        }
    }

    private void createResourceEvent(ResourceFlowType flowType, String virtualCurrency, String itemType,
                                     String itemId, float amount) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "resource");

        String event_id = flowType.toString() + ":" + virtualCurrency + ":" + itemType + ":" + itemId;
        event.put("event_id", event_id);
        event.putFloat("amount", amount);
        synchronized (queue) {
            queue.add(event);
        }
    }

    private void createErrorEvent(ErrorType severity, String message) {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "error");
        event.put("severity", severity.toString());
        event.put("message", message);
        synchronized (queue) {
            queue.add(event);
        }
    }

    protected void closeSession() {
        //FIXME needs to send the real session length
        AnnotatedEvent session_end_event = new AnnotatedEvent();
        session_end_event.put("category", "session_end");
        session_end_event.putInt("length", 60); // record locally how much time the session took and send it here. 60 is
        // an example
        sendEvent(session_end_event);

    }

    /**
     * send init request
     */
    protected void createInitRequest() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        String event = "[" + json.toJson(new InitEvent()) + "]";

        final Net.HttpRequest request = new Net.HttpRequest("POST");
        request.setUrl(url + game_key + "/init");
        request.setContent(event);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-type", "application/json");

        // authorization
        String hash = generateHash(event, secret_key);
        request.setHeader("Authorization", hash);

        canSendEvents = false;
        timeStampDiscrepancy = 0;

        //Execute and read response
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                canSendEvents = httpResponse.getStatus().getStatusCode() == 200;
                String resultAsString = httpResponse.getResultAsString();

                Gdx.app.debug(TAG, request.getContent() + "\n" +
                        httpResponse.getStatus().getStatusCode() + " " + resultAsString);
                if (canSendEvents) {
                    // calculate the client's time stamp discrepancy
                    try {
                        JsonValue response = new JsonReader().parse(resultAsString);
                        long serverTimestamp = response.getLong("server_ts") * 1000L;
                        timeStampDiscrepancy = serverTimestamp - TimeUtils.millis();
                        Gdx.app.log(TAG, "Successfuly initialized. Time stamp discrepancy in ms: " +
                                timeStampDiscrepancy);
                    } catch (Exception e) {
                        // do nothing
                    }

                    // add automated task to flush the qeue every 20 seconds
                    pingTask = Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {
                            flushQueue();
                        }
                    }, FLUSH_QUEUE_INTERVAL, FLUSH_QUEUE_INTERVAL);
                }
            }

            @Override
            public void failed(Throwable t) {
                canSendEvents = false;
            }

            @Override
            public void cancelled() {
                canSendEvents = false;
            }
        });
    }

    private void sendEvent(AnnotatedEvent eventJson) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        String event = "[" + json.toJson(eventJson) + "]";

        final Net.HttpRequest request = new Net.HttpRequest("POST");
        request.setUrl(url + game_key + "/events");
        request.setContent(event);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-type", "application/json");

        // authorization
        String hash = generateHash(event, secret_key);
        request.setHeader("Authorization", hash);

        //Execute and read response
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                Gdx.app.debug(TAG, request.getContent() + "\n" +
                        httpResponse.getStatus().getStatusCode() + " " + httpResponse.getResultAsString());
            }

            @Override
            public void failed(Throwable t) {
                failed();
            }

            @Override
            public void cancelled() {
                failed();
            }

            private void failed() {
                addLastFailed();
            }
        });
    }

    /**
     * @return if events are sent to gameanalytics after a successful login
     */
    public boolean canSend() {
        return canSendEvents;
    }

    public void setGameKey(String gamekey) {
        this.game_key = gamekey;
    }

    public void setGameSecretKey(String secretkey) {
        this.secret_key = secretkey;
    }

    public void setPlatform(Platform platform) {
        switch (platform) {
            case Windows:
                this.platform = "windows";
                return;
            case WebGL:
                this.platform = "webgl";
                return;
            case iOS:
                this.platform = "ios";
                return;
            case MacOS:
                this.platform = "mac_osx";
                return;
            case Android:
                this.platform = "android";
                return;
            case Linux:
                this.platform = "linux";
                return;
        }
    }

    ;

    public void setPlatformVersionString(String os_version) {
        this.os_version = os_version;
    }

    /**
     * @param build buildnumber of your game. This is a string, so you can also add build type information
     *              (e.g. "1818_debug", "1205_amazon", "1.5_tv")
     */
    public void setGameBuildNumber(String build) {
        this.build = build;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    /**
     * @param prefs your game's preferences. Needed to save user id and session information. All settings will be
     *              saved with "ga_" prefix to not interphere with your other settings
     */
    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
    }

    public void setCustom1(String custom1) {
        this.custom1 = custom1;
    }

    public void setCustom2(String custom2) {
        this.custom2 = custom2;
    }

    public void setCustom3(String custom3) {
        this.custom3 = custom3;
    }

    public enum ProgressionStatus {Start, Fail, Complete}

    public enum ResourceFlowType {Sink, Source}

    public enum ErrorType {debug, info, warning, error, critical}

    /**
     * Gameanalytics does not allow free definition of platforms. I did not find a documented list of supported
     * platforms, but these ones work
     */
    public enum Platform {
        Windows, Linux, Android, iOS, WebGL, MacOS
    }

    private class InitEvent implements Json.Serializable {
        @Override
        public void write(Json json) {
            json.writeValue("platform", platform);
            json.writeValue(os_version, os_version);
            json.writeValue("sdk_version", sdk_version);
        }

        @Override
        public void read(Json json, JsonValue jsonData) {
            // not implemented
        }
    }

    private class AnnotatedEvent implements Json.Serializable {
        private Map<String, Object> keyValues = new HashMap<>();

        public AnnotatedEvent() {
            //this is stored
            keyValues.put("client_ts", (Long) (TimeUtils.millis() + timeStampDiscrepancy) / 1000L);
        }

        @Override
        public void write(Json event) {
            event.writeValue("platform", platform);
            event.writeValue("os_version", os_version);
            event.writeValue("sdk_version", sdk_version);
            event.writeValue("device", device);
            event.writeValue("manufacturer", manufacturer);
            if (build != null)
                event.writeValue("build", build);
            event.writeValue("user_id", user_id);
            event.writeValue("v", 2);
            if (custom1 != null)
                event.writeValue("custom_01", custom1);
            if (custom2 != null)
                event.writeValue("custom_02", custom2);
            if (custom3 != null)
                event.writeValue("custom_03", custom3);

            //TODO use session num and id from this object to support flushing queue entries from last session
            event.writeValue("session_id", session_id);
            event.writeValue("session_num", session_num);

            //TODO custom fields

            for (String key : keyValues.keySet()) {
                event.writeValue(key, keyValues.get(key));
            }
        }

        @Override
        public void read(Json json, JsonValue jsonData) {
            // not supported
        }

        public void put(String name, String value) {
            keyValues.put(name, value);
        }

        public void putInt(String name, int value) {
            keyValues.put(name, value);
        }

        public void putFloat(String name, float value) {
            keyValues.put(name, value);
        }
    }
}
