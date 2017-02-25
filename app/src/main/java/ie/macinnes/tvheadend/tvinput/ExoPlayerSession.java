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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.player.EventLogger;
import ie.macinnes.tvheadend.player.ExoPlayerUtils;
import ie.macinnes.tvheadend.player.HtspDataSource;
import ie.macinnes.tvheadend.player.HtspExtractor;
import ie.macinnes.tvheadend.player.SimpleTvheadendPlayer;
import ie.macinnes.tvheadend.player.TvheadendTrackSelector;

public class ExoPlayerSession extends BaseSession implements ExoPlayer.EventListener {
    private static final String TAG = ExoPlayerSession.class.getName();

    private AccountManager mAccountManager;
    private Account mAccount;

    private SimpleHtspConnection mConnection;

    private SimpleExoPlayer mExoPlayer;
    private EventLogger mEventLogger;
    private TvheadendTrackSelector mTrackSelector;
    private DataSource.Factory mDataSourceFactory;
    private MediaSource mMediaSource;

    private ExtractorsFactory mExtractorsFactory;

    public ExoPlayerSession(Context context, Handler serviceHandler, SimpleHtspConnection connection) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");

        mConnection = connection;

        // Gather Details on the TVHeadend Instance
        mAccountManager = AccountManager.get(mContext);
        mAccount = AccountUtils.getActiveAccount(mContext);

        buildExoPlayer();
    }

    @Override
    protected boolean playChannel(int tvhChannelId) {
        // Stop any existing playback
        stopPlayback();

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        boolean htsp = sharedPreferences.getBoolean(Constants.KEY_HTSP_VIDEO_ENABLED, true);

        if (htsp) {
            buildHtspMediaSource(tvhChannelId);
        } else {
            buildHttpMediaSource(tvhChannelId);
        }

        mExoPlayer.prepare(mMediaSource);
        mExoPlayer.setPlayWhenReady(true);

        return true;
    }

    @Override
    protected void stopPlayback() {
        mExoPlayer.stop();

        if (mMediaSource != null) {
            mMediaSource.releaseSource();
        }
    }

    // TvInputService.Session Methods
    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurface (" + mSessionNumber + ")");
        mExoPlayer.setVideoSurface(surface);
        return false;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");
        mExoPlayer.setVolume(volume);
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        Log.d(TAG, "Session onSetCaptionEnabled: " + enabled + " (" + mSessionNumber + ")");
        super.onSetCaptionEnabled(enabled);
        setOverlayViewEnabled(enabled);
    }

    @Override
    public View onCreateOverlayView() {
        Log.d(TAG, "Session onCreateOverlayView (" + mSessionNumber + ")");

        SubtitleView view = new SubtitleView(mContext);
        mExoPlayer.setTextOutput(view);

        return view;
    }

    @Override
    public boolean onSelectTrack(int type, String trackId) {
        Log.d(TAG, "Session onSelectTrack: " + type + " / " + trackId + " (" + mSessionNumber + ")");
        return mTrackSelector.onSelectTrack(type, trackId);
    }

    // ExoPlayer.EventListener Methods
    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d(TAG, "Session onTracksChanged: " + trackGroups.length + " (" + mSessionNumber + ")");

        // Check currently mapped track for support
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = mTrackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO)
                    == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                showToast("Unsupported Video Track Selected");
            }
            if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO)
                    == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                showToast("Unsupported Audio Track Selected");
            }
            if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_TEXT)
                    == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                showToast("Unsupported Text Track Selected");
            }
        }

        // Process Available Tracks
        List<TvTrackInfo> tvTrackInfos = new ArrayList<>();

        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
            TrackGroup trackGroup = trackGroups.get(groupIndex);

            Log.d(TAG, "Processing trackGroup: " + groupIndex);

            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                Log.d(TAG, "Processing track: " + trackIndex);
                Format format = trackGroup.getFormat(trackIndex);

                TvTrackInfo tvTrackInfo = ExoPlayerUtils.buildTvTrackInfo(format);

                if (tvTrackInfo != null) {
                    tvTrackInfos.add(tvTrackInfo);
                }
            }
        }

        notifyTracksChanged(tvTrackInfos);

        // Process Selected Tracks
        for (int i = 0; i < trackSelections.length; i++) {
            TrackSelection selection = trackSelections.get(i);

            if (selection == null) {
                continue;
            }

            Format format = selection.getSelectedFormat();

            int trackType = MimeTypes.getTrackType(format.sampleMimeType);

            switch (trackType) {
                case C.TRACK_TYPE_VIDEO:
                    notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, format.id);
                    break;
                case C.TRACK_TYPE_AUDIO:
                    notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, format.id);
                    break;
                case C.TRACK_TYPE_TEXT:
                    notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, format.id);
                    break;
            }
        }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "Session onPlayerStateChanged: " + playbackState + " (" + mSessionNumber + ")");

        switch (playbackState) {
            case ExoPlayer.STATE_READY:
                notifyVideoAvailable();
                break;
            case ExoPlayer.STATE_BUFFERING:
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                break;
            case ExoPlayer.STATE_IDLE:
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
            case ExoPlayer.STATE_ENDED:
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity() {

    }

    // Misc Internal Methods
    private void buildExoPlayer() {
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveVideoTrackSelection.Factory(null);

        mTrackSelector = new TvheadendTrackSelector(videoTrackSelectionFactory);

        LoadControl loadControl = new DefaultLoadControl();

        int extensionRendererMode = SimpleExoPlayer.EXTENSION_RENDERER_MODE_PREFER;

        mExoPlayer = new SimpleTvheadendPlayer(
                mContext, mTrackSelector, loadControl, null, extensionRendererMode,
                ExoPlayerFactory.DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);

        mExoPlayer.addListener(this);

        // Add the EventLogger
        mEventLogger = new EventLogger(mTrackSelector);
        mExoPlayer.addListener(mEventLogger);
        mExoPlayer.setAudioDebugListener(mEventLogger);
        mExoPlayer.setVideoDebugListener(mEventLogger);

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        boolean htsp = sharedPreferences.getBoolean(Constants.KEY_HTSP_VIDEO_ENABLED, false);

        if (htsp) {
            buildHtspExoPlayer();
        } else {
            buildHttpExoPlayer();
        }
    }

    private void buildHtspExoPlayer() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        String streamProfile = sharedPreferences.getString(Constants.KEY_HTSP_STREAM_PROFILE, "htsp");

        // Produces DataSource instances through which media data is loaded.
        mDataSourceFactory = new HtspDataSource.Factory(mContext, mConnection, streamProfile);

        // Produces Extractor instances for parsing the media data.
        mExtractorsFactory = new HtspExtractor.Factory();
    }

    private void buildHttpExoPlayer() {
        String username = mAccount.name;
        String password = mAccountManager.getPassword(mAccount);

        // We need a local variable of this type here, so we can call the setDefaultRequestProperty
        // method.. Once we're done with that, we assign it over to mDataSourceFactory.
        DefaultHttpDataSourceFactory factory = new DefaultHttpDataSourceFactory(getUserAgent());

        // Create authentication headers
        Map<String, String> headers = MiscUtils.createBasicAuthHeader(username, password);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            factory.setDefaultRequestProperty(entry.getKey(), entry.getValue());
        }

        // Produces DataSource instances through which media data is loaded.
        mDataSourceFactory = factory;

        // Produces Extractor instances for parsing the media data.
        mExtractorsFactory = new DefaultExtractorsFactory();
    }

    private void buildHtspMediaSource(int tvhChannelId) {
        Uri videoUri = Uri.parse("htsp://" + tvhChannelId);

        // This is the MediaSource representing the media to be played.
        mMediaSource = new ExtractorMediaSource(videoUri,
                mDataSourceFactory, mExtractorsFactory, null, mEventLogger);
    }

    private void buildHttpMediaSource(int tvhChannelId) {
        String hostname = mAccountManager.getUserData(mAccount, Constants.KEY_HOSTNAME);
        String httpPort = mAccountManager.getUserData(mAccount, Constants.KEY_HTTP_PORT);
        String httpPath = mAccountManager.getUserData(mAccount, Constants.KEY_HTTP_PATH);

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        String streamProfile = sharedPreferences.getString(Constants.KEY_HTTP_STREAM_PROFILE, "tif");

        Uri videoUri;

        if (httpPath == null) {
            videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/stream/channelid/" + tvhChannelId + "?profile=" + streamProfile);
        } else {
            videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/" + httpPath + "/stream/channelid/" + tvhChannelId + "?profile=" + streamProfile);
        }

        // Hardcode a Test Video URI
//        videoUri = Uri.parse("http://10.5.1.22/test1.mp4"); // Multi Audio (eng 2.0 (AC3) and eng 5.1 (DTS)), Multi Subtitle (eng, rus, etc)
//        videoUri = Uri.parse("http://10.5.1.22/test2.mp4"); // Single Audio (und, 5.1), No Subtitle
//        videoUri = Uri.parse("http://10.5.1.22/test2-new2.mp4"); // Multi Audio (und, 2.0 AC3 and und, 5.1 AC3), No Subtitle
//        videoUri = Uri.parse("http://10.5.1.22/test5.mkv");

        // This is the MediaSource representing the media to be played.
        mMediaSource = new ExtractorMediaSource(videoUri,
                mDataSourceFactory, mExtractorsFactory, null, mEventLogger);
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

    private String getUserAgent() {
        return "android-tvheadend/" + BuildConfig.VERSION_NAME + " (Linux;Android " + Build.VERSION.RELEASE
                + ";ExoPlayer " + ExoPlayerLibraryInfo.VERSION + ")";
    }
}
