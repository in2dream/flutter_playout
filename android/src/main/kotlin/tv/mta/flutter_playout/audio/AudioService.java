package tv.mta.flutter_playout.audio;

import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AudioService extends Service {
    static private final String TAG = "AudioService";
    private AudioServiceBinder audioServiceBinder = new AudioServiceBinder(this);

    public AudioService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "on Start COMMAND !!!!");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return audioServiceBinder;
    }

    @Override
    public void onDestroy() {
        audioServiceBinder.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        audioServiceBinder.onDestroy();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        audioServiceBinder.onDestroy();
        return super.onUnbind(intent);
    }
}