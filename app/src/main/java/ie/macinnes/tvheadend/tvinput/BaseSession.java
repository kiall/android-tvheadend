package ie.macinnes.tvheadend.tvinput;

import android.content.Context;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.accessibility.CaptioningManager;

import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.tvheadend.TvContractUtils;

abstract public class BaseSession extends android.media.tv.TvInputService.Session implements Handler.Callback {
    protected static final String TAG = BaseSession.class.getName();
    protected static AtomicInteger sSessionCounter = new AtomicInteger();

    private static final int MSG_PLAY_CHANNEL = 1000;

    protected final Context mContext;
    protected final Handler mServiceHandler;
    protected final Handler mSessionHandler;
    protected final int mSessionNumber;
    protected final TvInputManager mTvInputManager;
    protected final CaptioningManager mCaptioningManager;

    protected Surface mSurface;
    protected float mVolume;
    protected boolean mCaptionEnabled;

    protected PlayChannelRunnable mPlayChannelRunnable;

    public BaseSession(Context context, Handler serviceHandler) {
        super(context);
        mContext = context;

        mServiceHandler = serviceHandler;
        mSessionHandler = new Handler(this);

        mSessionNumber = sSessionCounter.getAndIncrement();

        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        mCaptioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);

        mCaptionEnabled = mCaptioningManager.isEnabled();
    }

    @Override
    public boolean onTune(Uri channelUri) {
        Log.d(TAG, "Session onTune (" + mSessionNumber + "): " + channelUri.toString());

        // Notify we are busy tuning
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

        mServiceHandler.removeCallbacks(mPlayChannelRunnable);
        mPlayChannelRunnable = new PlayChannelRunnable(channelUri);
        mServiceHandler.post(mPlayChannelRunnable);

        return true;
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        Log.d(TAG, "Session onSetCaptionEnabled: " + enabled + " (" + mSessionNumber + ")");
        mCaptionEnabled = enabled;
    }

    @Override
    public void onRelease() {
        Log.d(TAG, "Session onRelease (" + mSessionNumber + ")");

        if (mServiceHandler!= null) {
            mServiceHandler.removeCallbacks(mPlayChannelRunnable);
        }

        stopPlayback();
    }

    @Override
    public void notifyVideoAvailable() {
        Log.d(TAG, "Notifying video is available");
        super.notifyVideoAvailable();
    }

    @Override
    public void notifyVideoUnavailable(int reason) {
        Log.d(TAG, "Notifying video is unavailable, reason: " + Integer.toString(reason));
        super.notifyVideoUnavailable(reason);
    }

    @Override
    public void notifyTrackSelected(int type, String trackId) {
        if (type == TvTrackInfo.TYPE_VIDEO) {
            Log.d(TAG, "Notifying video track selected: " + trackId);
        } else if (type == TvTrackInfo.TYPE_AUDIO) {
            Log.d(TAG, "Notifying audio track selected: " + trackId);
        } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
            Log.d(TAG, "Notifying subtitle track selected: " + trackId);
        }
        super.notifyTrackSelected(type, trackId);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PLAY_CHANNEL:
                return playChannel((int) msg.obj);
        }
        return false;
    }

    abstract protected boolean playChannel(int tvhChannelId);
    abstract protected void stopPlayback();

    private class PlayChannelRunnable implements Runnable {
        private final Uri mChannelUri;

        public PlayChannelRunnable(Uri channelUri) {
            mChannelUri = channelUri;
        }

        @Override
        public void run() {
            Integer tvhChannelId = TvContractUtils.getTvhChannelIdFromChannelUri(mContext, mChannelUri);

            if (tvhChannelId != null) {
                mSessionHandler.removeMessages(MSG_PLAY_CHANNEL);
                mSessionHandler.obtainMessage(MSG_PLAY_CHANNEL, tvhChannelId).sendToTarget();
            } else {
                Log.w(TAG, "Failed to get channel info for " + mChannelUri);
            }
        }
    }
}
