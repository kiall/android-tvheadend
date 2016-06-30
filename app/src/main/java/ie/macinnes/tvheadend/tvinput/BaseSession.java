package ie.macinnes.tvheadend.tvinput;

import android.content.Context;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.model.Channel;

abstract public class BaseSession extends android.media.tv.TvInputService.Session implements Handler.Callback {
    protected static final String TAG = BaseSession.class.getName();
    protected static AtomicInteger sSessionCounter = new AtomicInteger();

    private static final int MSG_PLAY_CHANNEL = 1000;

    protected final Context mContext;
    protected final Handler mServiceHandler;
    protected final Handler mSessionHandler;
    protected final int mSessionNumber;
    private final TvInputManager mTvInputManager;

    protected Surface mSurface;
    protected float mVolume;

    protected PlayChannelRunnable mPlayChannelRunnable;

    public BaseSession(Context context, Handler serviceHandler) {
        super(context);
        mContext = context;

        mServiceHandler = serviceHandler;
        mSessionHandler = new Handler(this);

        mSessionNumber = sSessionCounter.getAndIncrement();
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
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
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PLAY_CHANNEL:
                return playChannel((Channel) msg.obj);
        }
        return false;
    }

    abstract protected boolean playChannel(Channel channel);
    abstract protected void stopPlayback();

    private class PlayChannelRunnable implements Runnable {
        private final Uri mChannelUri;

        public PlayChannelRunnable(Uri channelUri) {
            mChannelUri = channelUri;
        }

        @Override
        public void run() {
            Channel channel = TvContractUtils.getChannelFromChannelUri(mContext, mChannelUri);

            if (channel != null) {
                mSessionHandler.removeMessages(MSG_PLAY_CHANNEL);
                mSessionHandler.obtainMessage(MSG_PLAY_CHANNEL, channel).sendToTarget();
            } else {
                Log.w(TAG, "Failed to get channel info for " + mChannelUri);
            }
        }
    }
}
