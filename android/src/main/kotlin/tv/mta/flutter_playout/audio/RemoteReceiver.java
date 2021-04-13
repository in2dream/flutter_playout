package tv.mta.flutter_playout.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public class RemoteReceiver extends BroadcastReceiver {
    static private final String TAG = "RemoteReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        try {

            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {

                final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

                if (event != null) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {

                        switch (event.getKeyCode()) {

                            case KeyEvent.KEYCODE_MEDIA_PAUSE:

                                AudioServiceBinder.service.pauseAudio();

                                break;

                            case KeyEvent.KEYCODE_MEDIA_PLAY:

                                AudioServiceBinder.service.startAudio(0, true);

                                break;

                            case KeyEvent.KEYCODE_MEDIA_NEXT:

                                AudioServiceBinder.service.nextTrack();

                                break;

                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:

                                AudioServiceBinder.service.prevTrack();

                                break;
                        }
                    }
                }
            }

        } catch (Exception e) { /* ignore */ }
    }
}
