import android.os.Build;

/**
 * Use this subclass on Android to set platform, version, device and manufacturer automatically
 * <p>
 * Created by Benjamin Schulte on 05.05.2018.
 */

public class AndroidGameAnalytics extends GameAnalytics {
    @Override
    public void initSession() {
        setPlatform(Platform.Android);
        setPlatformVersionString(Build.VERSION.RELEASE);
        setDevice(Build.DEVICE);
        setManufacturer(Build.MANUFACTURER);

        super.initSession();
    }
}
