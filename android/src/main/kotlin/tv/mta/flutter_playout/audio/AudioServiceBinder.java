package tv.mta.flutter_playout.audio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import tv.mta.flutter_playout.FlutterAVPlayer;
import tv.mta.flutter_playout.MediaItem;
import tv.mta.flutter_playout.PlayerNotificationUtil;
import tv.mta.flutter_playout.PlayerState;
import tv.mta.flutter_playout.R;

import static tv.mta.flutter_playout.PlayerNotificationUtil.getActionIntent;

public class AudioServiceBinder
        extends Binder
        implements FlutterAVPlayer, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String TAG = "AudioServiceBinder";

    /**
     * The notification channel id we'll send notifications too
     */
    private static final String mNotificationChannelId = "NotificationBarController";
    /**
     * Playback Rate for the MediaPlayer is always 1.0.
     */
    private static final float PLAYBACK_RATE = 1.0f;
    /**
     * The notification id.
     */
    public static final int NOTIFICATION_ID = 1;
    static AudioServiceBinder service;
    // This is the message signal that inform audio progress updater to update audio progress.
    final int UPDATE_AUDIO_PROGRESS_BAR = 1;
    final int UPDATE_PLAYER_STATE_TO_PAUSE = 2;
    final int UPDATE_PLAYER_STATE_TO_PLAY = 3;
    final int UPDATE_PLAYER_STATE_TO_COMPLETE = 4;
    final int UPDATE_AUDIO_DURATION = 5;
    final int UPDATE_PLAYER_STATE_TO_ERROR = 6;
    final int UPDATE_AUDIO = 7;

    private boolean _playerReady = false;
    public boolean isPlayerReady() {
        return _playerReady;
    }
    private NotificationCompat.Builder _notificationBuilder;

    private boolean isBound = true;

    private boolean isMediaChanging = false;

    /**
     * Whether the {@link MediaPlayer} broadcasted an error.
     */
    private boolean mReceivedError;

    private ArrayList<MediaItem> mediaQueue = new ArrayList<MediaItem>();
    private int currentMediaIndex = 0;

    private String audioFileUrl = "";

    private String title;

    private String subtitle;

    private Bitmap artwork;

    private String cover;

    private MediaPlayer audioPlayer = null;

    private int startPositionInMills = 0;

    // This Handler object is a reference to the caller activity's Handler.
    // In the caller activity's handler, it will update the audio play progress.
    private Handler audioProgressUpdateHandler;

    /**
     * The underlying {@link MediaSessionCompat}.
     */
    private MediaSessionCompat mMediaSessionCompat;

    private Context context;

    private Activity activity;

    private AudioService theService;

    AudioServiceBinder(AudioService service) {
        theService = service;
    }

    public AudioService getService() {
        return theService;
    }

    public NotificationCompat.Builder getNotificationBuilder() {
        return _notificationBuilder;
    }

    MediaPlayer getAudioPlayer() {
        return audioPlayer;
    }

    String getAudioFileUrl() {
        return audioFileUrl;
    }

    void setAudioFileUrl(String audioFileUrl) {
        this.audioFileUrl = audioFileUrl;
    }

    void setTitle(String title) {
        this.title = title;
    }

    void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    void setCover(String cover) { this.cover = cover; }

    void setAudioProgressUpdateHandler(Handler audioProgressUpdateHandler) {
        this.audioProgressUpdateHandler = audioProgressUpdateHandler;
    }

    private Context getContext() {
        return context;
    }

    void setContext(Context context) {
        this.context = context;
    }

    void setActivity(Activity activity) {
        this.activity = activity;
    }

    boolean isMediaChanging() {
        return isMediaChanging;
    }

    void setMediaChanging(boolean mediaChanging) {
        isMediaChanging = mediaChanging;
    }

    private void setAudioMetadata() {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, cover);

        if (_playerReady && audioPlayer.getDuration() > 0) {
            Log.d(TAG, "Set metadata duration: " + audioPlayer.getDuration());
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, audioPlayer.getDuration());
        }
        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        }
        MediaMetadataCompat metadata = builder.build();
        mMediaSessionCompat.setMetadata(metadata);
    }

    void startAudio(int startPositionInMills) {
        startAudio(startPositionInMills, false);
    }

    void startAudio(int startPositionInMills, boolean startForeground) {
        Log.d(TAG, "Start Audio: " + startPositionInMills + ", startForeground: " + startForeground);
        this.startPositionInMills = startPositionInMills;

        initAudioPlayer();

        if (audioPlayer != null && mMediaSessionCompat != null && mMediaSessionCompat.isActive()) {

            updatePlaybackState(PlayerState.PLAYING, startForeground);

            // Create update audio player state message.
            Message updateAudioProgressMsg = new Message();

            updateAudioProgressMsg.what = UPDATE_PLAYER_STATE_TO_PLAY;

            // Send the message to caller activity's update audio Handler object.
            audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);
        }

        service = this;
    }

    void seekAudio(int position) {
        if (_playerReady) {
            audioPlayer.seekTo(position * 1000);
        }
    }

    void pauseAudio() {

        if (audioPlayer != null) {

            if (audioPlayer.isPlaying()) {

                audioPlayer.pause();
            }

            updatePlaybackState(PlayerState.PAUSED);

            // Create update audio player state message.
            Message updateAudioProgressMsg = new Message();

            updateAudioProgressMsg.what = UPDATE_PLAYER_STATE_TO_PAUSE;

            // Send the message to caller activity's update audio Handler object.
            audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);
        }
    }

    void setQueue(ArrayList<MediaItem> queue)
    {
        Log.d(TAG, "set media queue: " + queue.size());
        mediaQueue = queue;
    }

    void setPlayIndex(int index)
    {
        Log.d(TAG, "set media index: " + index);
        currentMediaIndex = index;
    }

    void nextTrack() {
        if (audioPlayer != null) {
            final int nextIndex = currentMediaIndex + 1;
            if (mediaQueue.size() > nextIndex) {
                playQueueItemAtIndex(nextIndex);
            }
        }
    }

    void prevTrack() {
        if (audioPlayer != null) {
            final int prevIndex = currentMediaIndex - 1;
            if (prevIndex >= 0) {
                playQueueItemAtIndex(prevIndex);
            }
        }
    }

    void playQueueItemAtIndex(int index) {
        if (index == currentMediaIndex) {
            return;
        }

        Log.d(TAG, "playQueueItemAtIndex" + index);
        setPlayIndex(index);

        Message updateAudioMsg = new Message();
        updateAudioMsg.what = UPDATE_AUDIO;
        updateAudioMsg.arg1 = index;
        audioProgressUpdateHandler.sendMessage(updateAudioMsg);
    }

    void reset() {

        if (audioPlayer != null) {

            if (audioPlayer.isPlaying()) {

                audioPlayer.stop();
            }

            audioPlayer.reset();

            audioPlayer = null;

            updatePlaybackState(PlayerState.COMPLETE);

            _playerReady = false;
        }
    }

    void cleanPlayerNotification() {
        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void initAudioPlayer() {
        Log.d(TAG, "InitAudioPlayer: " + getAudioFileUrl());
        try {

            boolean isInit = false;
            if (audioPlayer == null) {
                isInit = true;
                audioPlayer = new MediaPlayer();

                audioPlayer.setOnPreparedListener(this);

                audioPlayer.setOnCompletionListener(this);

                audioPlayer.setOnErrorListener(this);
            }

            if (isMediaChanging || isInit) {
                Log.d(TAG, "Media is changing");
                if (isPlayerReady()) {
                    if (audioPlayer.isPlaying()) {
                        audioPlayer.stop();
                    }
                }

                if (!TextUtils.isEmpty(getAudioFileUrl())) {
                    isBound = false;
                    _playerReady = false;
                    audioPlayer.reset();
                    audioPlayer.setDataSource(getAudioFileUrl());
                    audioPlayer.prepareAsync();
                }
            } else {
                audioPlayer.start();
            }

        } catch (IOException ex) {
            mReceivedError = true;
        }
    }

    @Override
    public void onDestroy() {

        Log.d(TAG, "Destroy");
        isBound = false;

        try {

            //cleanPlayerNotification();

            if (audioPlayer != null) {

                if (audioPlayer.isPlaying()) {

                    audioPlayer.stop();
                }

                audioPlayer.reset();

                audioPlayer.release();

                audioPlayer = null;
            }

        } catch (Exception e) { /* ignore */ }
    }

    int getCurrentAudioPosition() {
        int ret = 0;

        if (audioPlayer != null) {

            ret = audioPlayer.getCurrentPosition();
        }

        return ret;
    }

    protected void updateNotificationProgress() {
        if (_notificationBuilder != null) {
            if (_playerReady) {
                if (audioPlayer.getDuration() > 0) {
                    Log.d(TAG, "Progress: " + audioPlayer.getDuration() + "/" + audioPlayer.getCurrentPosition());
                    _notificationBuilder.setProgress(audioPlayer.getDuration(), audioPlayer.getCurrentPosition(), false);
                } else {
                    _notificationBuilder.setProgress(100, 100, false);
                }
            } else {
                _notificationBuilder.setProgress(100, 0, false);
            }
        }
    }

    protected void reflectNotificationUpdate() {
        reflectNotificationUpdate(false);
    }

    protected void reflectNotificationUpdate(boolean startForeground) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification n = _notificationBuilder.build();

        if (startForeground) {
            theService.startForeground(NOTIFICATION_ID, n);
        }

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, n);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {

        _playerReady = true;

        isBound = true;

        setMediaChanging(false);

        if (startPositionInMills > 0) {
            mp.seekTo(startPositionInMills);
        }

        mp.start();

        ComponentName receiver = new ComponentName(context.getPackageName(),
                RemoteReceiver.class.getName());

        /* Create a new MediaSession */
        mMediaSessionCompat = new MediaSessionCompat(context,
                AudioServiceBinder.class.getSimpleName(), receiver, null);

        mMediaSessionCompat.setCallback(new MediaSessionCallback(audioPlayer));

        mMediaSessionCompat.setActive(true);

        if (cover != null && !cover.isEmpty()) {
            Log.d(TAG, "Set art image: " + cover);
            if (cover.startsWith("http")) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                StrictMode.setThreadPolicy(policy);
                try {
                    URL url = new URL(cover);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    artwork = BitmapFactory.decodeStream(input);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    artwork = null;
                }
            } else {
                artwork = null;
            }
        }

        setAudioMetadata();

        updatePlaybackState(PlayerState.PLAYING);

        /* This thread object will send update audio progress message to caller activity every 1 second */
        Thread updateAudioProgressThread = new Thread() {

            @Override
            public void run() {

                while (isBound) {

                    try {

                        if (audioPlayer != null && audioPlayer.isPlaying()) {

                            _sendEventMessage(UPDATE_AUDIO_PROGRESS_BAR);
//                            updateNotificationProgress();
//                            reflectNotificationUpdate();

                            try {

                                Thread.sleep(100);

                            } catch (InterruptedException ex) { /* ignore */ }

                        } else {

                            try {

                                Thread.sleep(100);

                            } catch (InterruptedException ex) { /* ignore */ }
                        }

                        // Create update audio duration message.
                        Message updateAudioDurationMsg = new Message();

                        updateAudioDurationMsg.what = UPDATE_AUDIO_DURATION;

                        // Send the message to caller activity's update audio progressbar Handler object.
                        audioProgressUpdateHandler.sendMessage(updateAudioDurationMsg);

                    } catch (Exception e) {

                        Log.e(TAG, "onPrepared:updateAudioProgressThread: ", e);
                    }
                }

                Log.d(TAG, "Stop loop");
            }
        };

        updateAudioProgressThread.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

        if (audioPlayer != null) {

            audioPlayer.pause();
            // update progress bar for the last time
            _sendEventMessage(UPDATE_AUDIO_PROGRESS_BAR);

            updatePlaybackState(PlayerState.PAUSED);

            _sendEventMessage(UPDATE_PLAYER_STATE_TO_COMPLETE);

            audioPlayer.seekTo(0);
        }
    }

    private void _sendEventMessage(int event) {
        // Create update audio progress message.
        Message updateAudioProgressMsg = new Message();

        updateAudioProgressMsg.what = event;

        // Send the message to caller activity's update audio progressbar Handler object.
        audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

        updatePlaybackState(PlayerState.PAUSED);

        // Create update audio player state message.
        Message updateAudioPlayerStateMessage = new Message();

        updateAudioPlayerStateMessage.what = UPDATE_PLAYER_STATE_TO_ERROR;

        Log.e("AudioServiceBinder", "onPlayerError: [what=" + what + "] [extra=" + extra + "]", null);
        String errorMessage = "";
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_IO:
                errorMessage = "MEDIA_ERROR_IO: File or network related operation error";
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                errorMessage = "MEDIA_ERROR_MALFORMED: Bitstream is not conforming to the related" +
                        " coding standard or file spec";
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                errorMessage = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:  The video is str" +
                        "eamed and its container is not valid for progressive playback i.e the vi" +
                        "deo's index (e.g moov atom) is not at the start of the file";
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                errorMessage = "MEDIA_ERROR_SERVER_DIED: Media server died";
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                errorMessage = "MEDIA_ERROR_TIMED_OUT: Some operation takes too long to complete," +
                        " usually more than 3-5 seconds";
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                errorMessage = "MEDIA_ERROR_UNKNOWN: Unspecified media player error";
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                errorMessage = "MEDIA_ERROR_UNSUPPORTED: Bitstream is conforming to the related c" +
                        "oding standard or file spec, but the media framework does not support th" +
                        "e feature";
                break;
            default:
                errorMessage = "MEDIA_ERROR_UNKNOWN: Unspecified media player error";
                break;
        }
        updateAudioPlayerStateMessage.obj = errorMessage;

        // Send the message to caller activity's update audio Handler object.
        audioProgressUpdateHandler.sendMessage(updateAudioPlayerStateMessage);

        return false;
    }

    private PlaybackStateCompat.Builder getPlaybackStateBuilder() {

        PlaybackStateCompat playbackState = mMediaSessionCompat.getController().getPlaybackState();

        return playbackState == null
                ? new PlaybackStateCompat.Builder()
                : new PlaybackStateCompat.Builder(playbackState);
    }

    private void updatePlaybackState(PlayerState playerState) {
        updatePlaybackState(playerState, true);
    }

    private void updatePlaybackState(PlayerState playerState, boolean startForeground) {
        Log.d(TAG, "Update Player State");
        if (mMediaSessionCompat == null) return;

        PlaybackStateCompat.Builder newPlaybackState = getPlaybackStateBuilder();

        long capabilities = getCapabilities(playerState);

        newPlaybackState.setActions(capabilities);

        int playbackStateCompat = PlaybackStateCompat.STATE_NONE;

        switch (playerState) {
            case PLAYING:
                playbackStateCompat = PlaybackStateCompat.STATE_PLAYING;
                break;
            case PAUSED:
                playbackStateCompat = PlaybackStateCompat.STATE_PAUSED;
                break;
            case BUFFERING:
                playbackStateCompat = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case IDLE:
                if (mReceivedError) {
                    playbackStateCompat = PlaybackStateCompat.STATE_ERROR;
                } else {
                    playbackStateCompat = PlaybackStateCompat.STATE_STOPPED;
                }
                break;
        }

        if (audioPlayer != null) {
            newPlaybackState.setState(playbackStateCompat,
                    (long) audioPlayer.getCurrentPosition(), PLAYBACK_RATE);
        }

        mMediaSessionCompat.setPlaybackState(newPlaybackState.build());

        updateNotification(capabilities, startForeground);
    }

    private @PlaybackStateCompat.Actions
    long getCapabilities(PlayerState playerState) {
        long capabilities = PlaybackStateCompat.ACTION_SEEK_TO;

        switch (playerState) {
            case PLAYING:
                capabilities |= PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case PAUSED:
                capabilities |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case BUFFERING:
                capabilities |= PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case IDLE:
                if (!mReceivedError) {
                    capabilities |= PlaybackStateCompat.ACTION_PLAY;
                }
                break;
        }

        Log.d(TAG, "should support capabilities: " + mediaQueue.size() + " currentIndex " + currentMediaIndex);
        if (mediaQueue.size() > 0) {
            if (!mReceivedError) {
                if (mediaQueue.size() - 1 > currentMediaIndex) {
                    Log.d(TAG, "add capabilities: skip next");
                    capabilities |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
                }
                if (currentMediaIndex > 0) {
                    Log.d(TAG, "add capabilities: skip prev");
                    capabilities |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
                }
            }
        }

        return capabilities;
    }

    @SuppressLint("RestrictedApi")
    private void updateNotification(long capabilities, boolean startForeground) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            createNotificationChannel();
        }

        if (_notificationBuilder == null) {
            _notificationBuilder = PlayerNotificationUtil.from(
                    activity, context, mMediaSessionCompat, mNotificationChannelId);
        }

        MediaControllerCompat controller = mMediaSessionCompat.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();
        int smallIcon = context.getResources().getIdentifier(
                "ic_notification_icon", "drawable", context.getPackageName());

        _notificationBuilder.setContentTitle(description.getTitle())
                .setOngoing(true)
                .setContentText(description.getSubtitle())
                .setLargeIcon(description.getIconBitmap())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mMediaSessionCompat.getSessionToken()))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(smallIcon)
                .setDeleteIntent(getActionIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));

        //updateNotificationProgress();
        _notificationBuilder.mActions.clear();

        if ((capabilities & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            _notificationBuilder.addAction(R.drawable.ic_skip_prev, "Skip to Previous",
                    getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        }

        if ((capabilities & PlaybackStateCompat.ACTION_PAUSE) != 0) {
            _notificationBuilder.addAction(R.drawable.ic_pause, "Pause",
                    getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE));
        }

        if ((capabilities & PlaybackStateCompat.ACTION_PLAY) != 0) {
            _notificationBuilder.addAction(R.drawable.ic_play, "Play",
                    getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY));
        }

        if ((capabilities & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            _notificationBuilder.addAction(R.drawable.ic_skip_next, "Skip to Next",
                    getActionIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT));
        }

        reflectNotificationUpdate(startForeground);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        CharSequence channelNameDisplayedToUser = "Notification Bar Controls";

        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel newChannel = new NotificationChannel(
                mNotificationChannelId, channelNameDisplayedToUser, importance);

        newChannel.setDescription("All notifications");

        newChannel.setShowBadge(false);

        newChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(newChannel);
        }
    }

    /**
     * A {@link android.support.v4.media.session.MediaSessionCompat.Callback} implementation for MediaPlayer.
     */
    private final class MediaSessionCallback extends MediaSessionCompat.Callback {

        MediaSessionCallback(MediaPlayer player) {
            audioPlayer = player;
        }

        @Override
        public void onPause() {
            audioPlayer.pause();
        }

        @Override
        public void onPlay() {
            audioPlayer.start();
        }

        @Override
        public void onSeekTo(long pos) {
            audioPlayer.seekTo((int) pos);
        }

        @Override
        public void onStop() {
            audioPlayer.stop();
        }
    }
}