package de.golfgl.gdxgameanalytics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.Queue;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.Timer;

import java.util.HashMap;
import java.util.Map;

/**
 * Gameanalytics.com client for libGDX
 * <p>
 * Created by Benjamin Schulte on 05.05.2018 based up on example implementation
 * https://s3.amazonaws.com/download.gameanalytics.com/examples/GameAnalytics+REST+API+example.java
 * <p>
 * (That's the reason why this code looks like made by a server dev - it actually is. Improvements welcome)
 */

public class GameAnalytics {
    protected static final String URL_SANDBOX = "http://sandbox-api.gameanalytics.com/v2/";
    protected static final String TAG = "Gameanalytics";
    private final static String sdk_version = "rest api v2";
    private static final int FLUSH_QUEUE_INTERVAL = 20;
    private static final String URL_GAMEANALYTICS = "https://api.gameanalytics.com/v2/";
    private static final int MAX_EVENTS_SENT = 100;
    private static final int MAX_EVENTS_CACHED = 1000;

    // possible TODO: Timer fires on foreground thread, building and compressing content should be done in background
    protected Timer.Task pingTask;

    protected String url = URL_GAMEANALYTICS;
    protected boolean flushingQueue;
    private String game_key = null;
    private String secret_key = null;
    //dimension information
    private String platform = null;
    private String os_version = null;
    private String device = "unknown";
    private String manufacturer = "unknown";
    //game information
    private String build;
    //user information
    private String user_id = null;
    private String session_id;
    private int session_num = 0;
    private String custom1;
    private String custom2;
    private String custom3;
    //SDK status - this is false when not initialized or initializing failed
    private boolean connectionInitializing = false;
    private boolean connectionInitialized = false;
    private int nextQueueFlushInSeconds = 0;
    private final Queue<AnnotatedEvent> waitingQueue = new Queue<>();
    private final Queue<AnnotatedEvent> sendingQueue = new Queue<>();
    private int failedFlushAttempts;
    private long timeStampDiscrepancy;
    private long sessionStartTimestamp;
    private Preferences prefs;

    /**
     * initializes and starts the session. Make sure you have set all neccessary parameters before calling this
     * This can be called but twice, but if it is called when a session is still ongoing, it just resets the session
     * start time.
     * <p>
     * Call this on game start and on resume.
     */
    public void startSession() {
        if (sessionStartTimestamp > 0 && connectionInitialized) {
            Gdx.app.log(TAG, "No new session started. Session still ongoing");
            sessionStartTimestamp = TimeUtils.millis();
            return;
        }

        if (game_key == null || secret_key == null)
            throw new IllegalStateException("You must set your game key and secret key");

        if (platform == null)
            setPlatform(GwtIncompatibleStuff.getDefaultPlatform(Gdx.app.getType()));

        if (os_version == null)
            throw new IllegalStateException("You need to set a os version");

        if (prefs == null)
            Gdx.app.log(TAG, "You did not set up preferences. Session and user tracking will not work without it");

        loadOrInitUserStringAndSessionNum();

        session_id = GwtIncompatibleStuff.generateUuid();

        submitInitRequest();
        // start session is called if request is successful
    }

    private void loadOrInitUserStringAndSessionNum() {
        if (prefs != null) {
            user_id = prefs.getString("ga_userid", null);
            session_num = prefs.getInteger("ga_sessionnum", 0);
        }

        if (user_id == null || user_id.isEmpty()) {
            Gdx.app.log(TAG, "No user id found. Generating a new one.");
            user_id = GwtIncompatibleStuff.generateUuid();

            if (prefs != null)
                prefs.putString("ga_userid", user_id);
        }

        session_num++;

        if (prefs != null) {
            prefs.putInteger("ga_sessionnum", session_num);
            prefs.flush();
        }
    }

    private int loadAndIncrementTransactionNum() {
        if (prefs == null)
            return 0;

        int transactionNum = prefs.getInteger("ga_transactionnum", 0);
        transactionNum++;
        prefs.putInteger("ga_transactionnum", transactionNum);
        prefs.flush();
        return transactionNum;
    }

    /**
     * gets called every second by pingtask
     */
    protected void flushQueue() {
        if (!connectionInitialized || flushingQueue)
            return;

        // countdown to flush
        if (nextQueueFlushInSeconds > 0) {
            nextQueueFlushInSeconds -= 1;
            return;
        }

        if (waitingQueue.size == 0 && sendingQueue.size == 0)
            return;

        flushingQueue = true;
        nextQueueFlushInSeconds = FLUSH_QUEUE_INTERVAL;

        StringBuilder payload = new StringBuilder();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        synchronized (waitingQueue) {
            while (sendingQueue.size < MAX_EVENTS_SENT && waitingQueue.size > 0)
                sendingQueue.addLast(waitingQueue.removeFirst());

            Gdx.app.debug(TAG, "Sending queue with " + sendingQueue.size + " events");
            payload.append("[");
            for (int i = 0; i < sendingQueue.size; i++) {
                payload.append(json.toJson(sendingQueue.get(i)));
                if (i != sendingQueue.size - 1)
                    payload.append(",");
            }
        }
        payload.append("]");

        final Net.HttpRequest request = createHttpRequest(this.url + game_key + "/events", payload.toString());
        //Execute and read response
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                synchronized (waitingQueue) {
                    sendingQueue.clear();
                }

                int statusCode = httpResponse.getStatus().getStatusCode();
                String resultAsString = httpResponse.getResultAsString();

                if (statusCode == 200)
                    Gdx.app.debug(TAG, statusCode + " " + resultAsString);
                else
                    Gdx.app.error(TAG, statusCode + " " + resultAsString);

                failedFlushAttempts = 0;
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
                Gdx.app.error(TAG, "Could not send events in queue - probably offline");
                // lengthen the time to the next waitingQueue flush after a fail, but not more than 180 seconds
                failedFlushAttempts = Math.min(failedFlushAttempts + 1, 180 / FLUSH_QUEUE_INTERVAL);
                nextQueueFlushInSeconds = FLUSH_QUEUE_INTERVAL * (failedFlushAttempts + 1);
                Gdx.app.debug(TAG, "Next flush attempt in " + nextQueueFlushInSeconds + " seconds");
                flushingQueue = false;
            }
        });
    }

    private Net.HttpRequest createHttpRequest(String url, String payload) {
        final Net.HttpRequest request = new Net.HttpRequest("POST");
        request.setUrl(url);
        String hash = GwtIncompatibleStuff.setHttpRequestContent(request, payload, secret_key);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-type", "application/json");
        request.setHeader("Authorization", hash);
        return request;
    }

    private void addToWaitingQueue(AnnotatedEvent event) {
        while (waitingQueue.size > MAX_EVENTS_CACHED)
            waitingQueue.removeFirst();

        waitingQueue.addLast(event);
    }

    private void submitStartSessionRequest() {
        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "user");
        synchronized (waitingQueue) {
            addToWaitingQueue(event);
        }
    }

    public void submitDesignEvent(String event_id) {
        if (!isInitialized())
            return;

        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "design");
        event.put("event_id", event_id);
        synchronized (waitingQueue) {
            Gdx.app.debug(TAG, "Queuing design event");
            addToWaitingQueue(event);
        }
    }

    public void submitDesignEvent(String event_id, float value) {
        if (!isInitialized())
            return;

        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "design");
        event.put("event_id", event_id);
        event.putFloat("value", value);
        synchronized (waitingQueue) {
            Gdx.app.debug(TAG, "Queuing design event");
            addToWaitingQueue(event);
        }
    }

    /**
     * Submits a payment transaction to GameAnalytics
     *
     * @param itemType category for items
     * @param itemId   identifier for what has been purchased
     * @param amount   in cents
     * @param currency see http://openexchangerates.org/currencies.json
     */
    public void submitBusinessEvent(String itemType, String itemId, int amount, String currency) {
        if (!isInitialized())
            return;

        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "business");
        event.put("event_id", itemType + ":" + itemId);
        event.putInt("amount", amount);
        event.put("currency", currency);
        event.putInt("transaction_num", loadAndIncrementTransactionNum());
        synchronized (waitingQueue) {
            Gdx.app.debug(TAG, "Queuing business event");
            addToWaitingQueue(event);
        }
    }

    public void submitProgressionEvent(ProgressionStatus status, String progression01, String progression02,
                                       String progression03) {
        submitProgressionEvent(status, progression01, progression02, progression03, 0, 0);
    }

    public void submitProgressionEvent(ProgressionStatus status, String progression01, String progression02,
                                       String progression03, int score, int attemptNum) {
        if (!isInitialized())
            return;

        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "progression");

        String event_id = getStatusString(status) + ":" + progression01;
        if (progression02.length() > 0) {
            event_id += ":" + progression02;
        }
        if (progression03.length() > 0) {
            event_id += ":" + progression03;
        }
        event.put("event_id", event_id);

        if (status == ProgressionStatus.Complete || status == ProgressionStatus.Fail) {
            if (attemptNum > 0)
                event.putInt("attempt_num", attemptNum);
            if (score > 0)
                event.putInt("score", score);
        }
        synchronized (waitingQueue) {
            Gdx.app.debug(TAG, "Queuing progression event");
            addToWaitingQueue(event);
        }
    }

    private String getStatusString(ProgressionStatus status) {
        switch (status) {
            case Start:
                return "Start";
            case Fail:
                return "Fail";
            default:
                return "Complete";
        }
    }

    public void submitResourceEvent(ResourceFlowType flowType, String virtualCurrency, String itemType,
                                     String itemId, float amount) {
        if (!isInitialized())
            return;

        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "resource");

        String event_id = getFlowTypeString(flowType) + ":" + virtualCurrency + ":" + itemType + ":" + itemId;
        event.put("event_id", event_id);
        event.putFloat("amount", amount);
        synchronized (waitingQueue) {
            Gdx.app.debug(TAG, "Queuing resource event");
            addToWaitingQueue(event);
        }
    }

    private String getFlowTypeString(ResourceFlowType flowType) {
        switch (flowType) {
            case Sink:
                return "Sink";
            default:
                return "Source";
        }
    }

    /**
     * submits an error event
     *
     * @param severity
     * @param message
     */
    public void submitErrorEvent(ErrorType severity, String message) {
        if (!isInitialized())
            return;

        if (message.length() > 8000)
            message = message.substring(0, 8000);

        AnnotatedEvent event = new AnnotatedEvent();
        event.put("category", "error");
        event.put("severity", getSeverityString(severity));
        event.put("message", message);
        synchronized (waitingQueue) {
            Gdx.app.debug(TAG, "Queuing error event (" + message + ")");
            addToWaitingQueue(event);
        }
    }


    /**
     * submits a throwable immediately and blocks the thread until it is sent
     * (with a max wait time of three seconds)
     *
     * @param e Exception
     * @return Stacktrace as a string
     * @throws InterruptedException
     */
    protected String sendThrowableAsErrorEventSync(Throwable e) throws InterruptedException {
        String exceptionAsString = GwtIncompatibleStuff.getThrowableStacktraceAsString(e);

        int waitTime = 0;
        // let's wait three seconds in case initializing is not yet done
        while ((connectionInitializing || flushingQueue) && waitTime < 30) {
            Thread.sleep(100);
            waitTime++;
        }

        submitErrorEvent(ErrorType.error, exceptionAsString);
        flushQueueImmediately();

        waitTime = 0;
        // let's wait three seconds in case sending is slow
        while (flushingQueue && waitTime < 30) {
            Thread.sleep(100);
            waitTime++;
        }

        return exceptionAsString;
    }

    private String getSeverityString(ErrorType severity) {
        switch (severity) {
            case info:
                return "info";
            case debug:
                return "debug";
            case error:
                return "error";
            case critical:
                return "critical";
            default:
                return "warning";
        }
    }

    /**
     * closes the ongoing session. Call this on your game's pause() method
     * <p>
     * This is failsafe - if no session is open, nothing is done
     */
    public void closeSession() {
        //TODO should get saved for next time, but works well on Android (not on Desktop though)
        if (sessionStartTimestamp > 0 && connectionInitialized) {
            AnnotatedEvent session_end_event = new AnnotatedEvent();
            session_end_event.put("category", "session_end");
            session_end_event.putInt("length", (int) ((TimeUtils.millis() - sessionStartTimestamp) / 1000L));

            //this will not work if queue is full. But in that case, the message will probably never get sent
            addToWaitingQueue(session_end_event);
            flushQueueImmediately();
        }
        sessionStartTimestamp = 0;
    }

    public void flushQueueImmediately() {
        nextQueueFlushInSeconds = 0;
        flushQueue();
    }

    /**
     * send init request
     */
    protected void submitInitRequest() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        String event = "[" + json.toJson(new InitEvent()) + "]";

        final Net.HttpRequest request = createHttpRequest(url + game_key + "/init", event);

        connectionInitialized = false;
        connectionInitializing = true;
        timeStampDiscrepancy = 0;

        //Execute and read response
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                connectionInitialized = httpResponse.getStatus().getStatusCode() == 200;
                connectionInitializing = false;
                String resultAsString = httpResponse.getResultAsString();

                if (connectionInitialized) {
                    Gdx.app.debug(TAG, httpResponse.getStatus().getStatusCode() + " " + resultAsString);
                    // calculate the client's time stamp discrepancy

                    sessionStartTimestamp = TimeUtils.millis();
                    try {
                        JsonValue response = new JsonReader().parse(resultAsString);
                        long serverTimestamp = response.getLong("server_ts") * 1000L;
                        timeStampDiscrepancy = serverTimestamp - TimeUtils.millis();
                        Gdx.app.log(TAG, "Session open. Time stamp discrepancy in ms: " +
                                timeStampDiscrepancy);
                    } catch (Exception e) {
                        // do nothing
                    }

                    submitStartSessionRequest();
                    flushQueueImmediately();

                    // add automated task to flush the qeue every 20 seconds
                    // FIXME if this is called while lockscreen is on, task is not working. Mostly a problem when
                    // testing with adb
                    if (pingTask == null)
                        pingTask = Timer.schedule(new Timer.Task() {
                            @Override
                            public void run() {
                                flushQueue();
                            }
                        }, 1, 1);
                } else
                    Gdx.app.error(TAG, "Connection attempt failed: " + httpResponse.getStatus().getStatusCode() + " "
                            + resultAsString);
            }

            @Override
            public void failed(Throwable t) {
                cancelled();
            }

            @Override
            public void cancelled() {
                connectionInitialized = false;
                connectionInitializing = false;
                Gdx.app.error(TAG, "Could not connect to GameAnalytics - suspended");
            }
        });
    }

    /**
     * @return if events are sent to gameanalytics after a successful login
     */
    public boolean isInitialized() {
        return connectionInitialized;
    }

    /**
     * @return current time on server. Only valid after successful initialization, so check {@link #isInitialized()}
     * before trusting this value
     */
    public long getCurrentServerTime() {
        return TimeUtils.millis() + timeStampDiscrepancy;
    }

    public void setGameKey(String gamekey) {
        this.game_key = gamekey;
    }

    public void setGameSecretKey(String secretkey) {
        this.secret_key = secretkey;
    }

    public String getPlatform() {
        return platform;
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

    public String getPlatformVersionString() {
        return os_version;
    }

    /**
     * @param os_version must match [0-9]{0,5}(\.[0-9]{0,5}){0,2}$. Unique value limit is 255
     * @return if your version String matched the expected regex.
     */
    public boolean setPlatformVersionString(String os_version) {
        this.os_version = os_version;
        boolean matches = os_version.matches("[0-9]{0,5}(\\.[0-9]{0,5}){0,2}");
        return matches;
    }

    public String getGameBuildNumber() {
        return build;
    }

    /**
     * @param build buildnumber of your game. This is a string, so you can also add build type information
     *              (e.g. "1818_debug", "1205_amazon", "1.5_tv") - but be aware, limit of unit strings is 100
     */
    public void setGameBuildNumber(String build) {
        this.build = build;
    }

    /**
     * @param device device information. Unqiue value limit is 500
     */
    public void setDevice(String device) {
        if (device.length() > 30)
            device = device.substring(0, 30);

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

    /**
     * @param custom1 value for custom dimension. 50 different values supported at max, max length 32
     */
    public void setCustom1(String custom1) {
        this.custom1 = custom1;
    }

    /**
     * @param custom2 value for custom dimension. 50 different values supported at max, max length 32
     */
    public void setCustom2(String custom2) {
        this.custom2 = custom2;
    }

    /**
     * @param custom3 value for custom dimension. 50 different values supported at max, max length 32
     */
    public void setCustom3(String custom3) {
        this.custom3 = custom3;
    }

    public enum ProgressionStatus {Start, Fail, Complete}

    public enum ResourceFlowType {Sink, Source}

    public enum ErrorType {debug, info, warning, error, critical}

    /**
     * Gameanalytics does not allow free definition of platforms.
     *
     * See https://gameanalytics.com/docs/item/rest-api-doc#default-annotations-shared for all acceptable platforms
     */
    public enum Platform {
        Windows, Linux, Android, iOS, WebGL, MacOS
    }

    private class InitEvent implements Json.Serializable {
        @Override
        public void write(Json json) {
            json.writeValue("platform", platform);
            json.writeValue("os_version", platform + " " + os_version);
            json.writeValue("sdk_version", sdk_version);
        }

        @Override
        public void read(Json json, JsonValue jsonData) {
            // not implemented
        }
    }

    private class AnnotatedEvent implements Json.Serializable {
        private Map<String, Object> keyValues = new HashMap<>();
        private String sessionId;
        private int sessionNum;

        public AnnotatedEvent() {
            //this is stored
            keyValues.put("client_ts", (Long) getCurrentServerTime() / 1000L);
            this.sessionId = session_id;
            this.sessionNum = session_num;
        }

        @Override
        public void write(Json event) {
            event.writeValue("platform", platform);
            event.writeValue("os_version", platform + " " + os_version);
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

            event.writeValue("session_id", sessionId);
            event.writeValue("session_num", sessionNum);

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
