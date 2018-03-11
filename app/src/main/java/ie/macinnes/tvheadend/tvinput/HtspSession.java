/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ie.macinnes.tvheadend.tvinput;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.PlaybackParams;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.player.TvheadendPlayer;

// TODO: Rename?
public class HtspSession extends TvInputService.Session implements TvheadendPlayer.Listener {
    private static final String TAG = HtspSession.class.getName();
    private static final AtomicInteger sSessionCounter = new AtomicInteger();

    private final Context mContext;
    private final int mSessionNumber;
    private final Handler mHandler;
    private final CaptioningManager mCaptioningManager;
    private final SharedPreferences mSharedPreferences;

    private final TvheadendPlayer mTvheadendPlayer;

    private Runnable mPlayChannelRunnable;

    public HtspSession(Context context, SimpleHtspConnection connection) {
        super(context);

        mContext = context;
        mSessionNumber = sSessionCounter.getAndIncrement();
        mHandler = new Handler();
        mCaptioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);

        mSharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        Log.d(TAG, "HtspSession created (" + mSessionNumber + ")");

        mTvheadendPlayer = new TvheadendPlayer(mContext, connection, this);

        setOverlayViewEnabled(true);
    }

    @Override
    public void onRelease() {
        Log.d(TAG, "Session onRelease (" + mSessionNumber + ")");
        mTvheadendPlayer.release();
    }

    // TvInputService.Session Methods
    @Override
    public boolean onTune(Uri channelUri) {
        // Start Playback of a Live Channel
        Log.d(TAG, "Session onTune (" + mSessionNumber + "): " + channelUri.toString());

        // Notify we are busy tuning
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

        mHandler.removeCallbacks(mPlayChannelRunnable);
        mPlayChannelRunnable = new PlayChannelRunnable(channelUri);
        mHandler.post(mPlayChannelRunnable);

        return true;
    }

    @Override
    public void onTimeShiftPlay(Uri recordedProgramUri) {
        // Start Playback of a Recorded Program
        Log.d(TAG, "Session onTimeShiftPlay (" + mSessionNumber + "): " + recordedProgramUri.toString());

        // Notify we are busy tuning
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

        mHandler.removeCallbacks(mPlayChannelRunnable);
        mPlayChannelRunnable = new PlayRecordedProgramRunnable(recordedProgramUri);
        mHandler.post(mPlayChannelRunnable);
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        Log.d(TAG, "Session onSetCaptionEnabled: " + enabled + " (" + mSessionNumber + ")");
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurface (" + mSessionNumber + ")");
        mTvheadendPlayer.setSurface(surface);
        return true;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");
        mTvheadendPlayer.setVolume(volume);
    }

    @Override
    public View onCreateOverlayView() {
        Log.d(TAG, "Session onCreateOverlayView (" + mSessionNumber + ")");

        return mTvheadendPlayer.getOverlayView(
                mCaptioningManager.getUserStyle(), mCaptioningManager.getFontScale());
    }

    @Override
    public boolean onSelectTrack(int type, String trackId) {
        Log.d(TAG, "Session selectTrack: " + type + " / " + trackId + " (" + mSessionNumber + ")");
        return mTvheadendPlayer.selectTrack(type, trackId);
    }

    // TvheadendPlayer.Listener Methods
    @Override
    public void onTracksChanged(List<TvTrackInfo> tracks, SparseArray<String> selectedTracks) {
        Log.d(TAG, "Session : " + tracks.size() + " (" + mSessionNumber + ")");

        notifyTracksChanged(tracks);

        for (int i = 0; i < selectedTracks.size(); i++) {
            final int selectedTrackType = selectedTracks.keyAt(i);
            final String selectedTrackId = selectedTracks.get(selectedTrackType);

            notifyTrackSelected(selectedTrackType, selectedTrackId);
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "Session onPlayerStateChanged: " + playbackState + " (" + mSessionNumber + ")");

        switch (playbackState) {
            case Player.STATE_READY:
                notifyVideoAvailable();
                break;
            case Player.STATE_BUFFERING:
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                break;
            case Player.STATE_ENDED:
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        // Don't care about this event here
    }

    @Override
    public void onTimeShiftPause() {
        Log.d(TAG, "onTimeShiftPause");
        mTvheadendPlayer.pause();
    }

    @Override
    public void onTimeShiftResume() {
        Log.d(TAG, "onTimeShiftResume");
        mTvheadendPlayer.resume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PlaybackParams normalParams = new PlaybackParams();
            normalParams.setSpeed(1);
            onTimeShiftSetPlaybackParams(normalParams);
        }
    }

    @Override
    public void onTimeShiftSeekTo(long timeMs) {
        Log.d(TAG, "onTimeShiftSeekTo: " + timeMs);

        mTvheadendPlayer.seek(timeMs);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
        Log.d(TAG, "onTimeShiftSetPlaybackParams: Speed: " + params.getSpeed());

        mTvheadendPlayer.setPlaybackParams(params);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public long onTimeShiftGetStartPosition() {
        return mTvheadendPlayer.getTimeshiftStartPosition();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public long onTimeShiftGetCurrentPosition() {
        return mTvheadendPlayer.getTimeshiftCurrentPosition();
    }

    // Inner Classes
    private class PlayChannelRunnable implements Runnable {
        private final Uri mChannelUri;

        public PlayChannelRunnable(Uri channelUri) {
            mChannelUri = channelUri;
        }

        private boolean tune(int tvhChannelId) {
            Log.i(TAG, "Start playback of channel");
            Uri channelUri = Uri.parse("htsp://channel/" + tvhChannelId);

            mTvheadendPlayer.open(channelUri);
            mTvheadendPlayer.play();

            boolean timeshiftEnabled = mSharedPreferences.getBoolean(
                    Constants.KEY_TIMESHIFT_ENABLED,
                    mContext.getResources().getBoolean(R.bool.pref_default_timeshift_enabled));

            if (timeshiftEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }

            return true;
        }

        @Override
        public void run() {
            Integer tvhChannelId = TvContractUtils.getTvhChannelIdFromChannelUri(mContext, mChannelUri);

            if (tvhChannelId != null) {
                tune(tvhChannelId);
            } else {
                Log.w(TAG, "Failed to get channel info for " + mChannelUri);
            }
        }
    }

    private class PlayRecordedProgramRunnable implements Runnable {
        private final Uri mRecordedProgramUri;

        public PlayRecordedProgramRunnable(Uri recordedProgramUri) {
            mRecordedProgramUri = recordedProgramUri;
        }

        private boolean tune(int dvrEntryId) {
            Log.i(TAG, "Start playback of DVR entry");
            Uri recordedProgramUri = Uri.parse("htsp://dvrfile/" + dvrEntryId);

            mTvheadendPlayer.open(recordedProgramUri);
            mTvheadendPlayer.play();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }

            return true;
        }

        @Override
        public void run() {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                return;
            }

            Integer tvhDvrEntryId = TvContractUtils.getTvhDvrEntryIdFromRecordedProgramUri(mContext, mRecordedProgramUri);

            if (tvhDvrEntryId != null) {
                tune(tvhDvrEntryId);
            } else {
                Log.w(TAG, "Failed to get recorded program info for " + mRecordedProgramUri);
            }
        }
    }
}
