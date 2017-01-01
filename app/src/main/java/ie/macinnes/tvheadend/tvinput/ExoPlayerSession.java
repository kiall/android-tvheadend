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
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
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
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.player.HttpDataSourceFactory;
import ie.macinnes.tvheadend.player.SimpleTvheadendPlayer;
import ie.macinnes.tvheadend.player.TvheadendTrackSelector;

public class ExoPlayerSession extends BaseSession implements ExoPlayer.EventListener {
    private static final String TAG = ExoPlayerSession.class.getName();

    private SimpleExoPlayer mExoPlayer;
    private TvheadendTrackSelector mTrackSelector;
    private MediaSource mMediaSource;

    public ExoPlayerSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");

        buildExoPlayer();
    }

    @Override
    protected boolean playChannel(int tvhChannelId) {
        // Stop any existing playback
        stopPlayback();

        buildMediaSource(tvhChannelId);

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

                Log.d(TAG, "Processing track: " + ExoPlayerUtils.buildTrackName(format));

                if (format.id == null) {
                    Log.e(TAG, "Track ID invalid, skipping");
                    continue;
                }

                TvTrackInfo.Builder builder;
                int trackType = MimeTypes.getTrackType(format.sampleMimeType);

                switch (trackType) {
                    case C.TRACK_TYPE_VIDEO:
                        builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, format.id);
                        builder.setVideoFrameRate(format.frameRate);
                        if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                builder.setVideoWidth(format.width);
                                builder.setVideoHeight(format.height);
                                builder.setVideoPixelAspectRatio(format.pixelWidthHeightRatio);
                            } else {
                                builder.setVideoWidth((int) (format.width * format.pixelWidthHeightRatio));
                                builder.setVideoHeight(format.height);
                            }
                        }
                        break;

                    case C.TRACK_TYPE_AUDIO:
                        builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, format.id);
                        builder.setAudioChannelCount(format.channelCount);
                        builder.setAudioSampleRate(format.sampleRate);
                        break;

                    case C.TRACK_TYPE_TEXT:
                        builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, format.id);
                        break;

                    default:
                        Log.e(TAG, "Unsupported track type: " + format.sampleMimeType);
                        continue;
                }

                if (!TextUtils.isEmpty(format.language)
                        && !format.language.equals("und")
                        && !format.language.equals("nar")
                        && !format.language.equals("syn")) {
                    builder.setLanguage(format.language);
                }

                // TODO: Determine where the Description is used..
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    builder.setDescription(ExoPlayerUtils.buildTrackName(format));
//                }

                tvTrackInfos.add(builder.build());
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
    }

    private void buildMediaSource(int tvhChannelId) {
        // Gather Details on the TVHeadend Instance
        AccountManager accountManager = AccountManager.get(mContext);
        Account account = AccountUtils.getActiveAccount(mContext);

        String username = account.name;
        String password = accountManager.getPassword(account);
        String hostname = accountManager.getUserData(account, Constants.KEY_HOSTNAME);
        String httpPort = accountManager.getUserData(account, Constants.KEY_HTTP_PORT);
        String httpPath = accountManager.getUserData(account, Constants.KEY_HTTP_PATH);

        // Create authentication headers and streamUri
        Map<String, String> headers = MiscUtils.createBasicAuthHeader(username, password);
        Uri videoUri;

        if (httpPath == null) {
            videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/stream/channelid/" + tvhChannelId + "?profile=tif");
        } else {
            videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/" + httpPath + "/stream/channelid/" + tvhChannelId + "?profile=tif");
        }

        // Hardcode a Test Video URI
//        videoUri = Uri.parse("http://10.5.1.22/test1.mp4"); // Multi Audio (eng 2.0 and eng 5.1), Multi Subtitle (eng, rus, etc)
//        videoUri = Uri.parse("http://10.5.1.22/test2.mp4"); // Single Audio (und, 5.1), No Subtitle
//        videoUri = Uri.parse("http://10.5.1.22/test2-new2.mp4"); // Multi Audio (und, 2.0 and und, 5.1 ), No Subtitle
//        videoUri = Uri.parse("http://10.5.1.22/test5.mkv");

        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new HttpDataSourceFactory(headers);

        // Produces Extractor instances for parsing the media data.
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

        // This is the MediaSource representing the media to be played.
        mMediaSource = new ExtractorMediaSource(videoUri,
                dataSourceFactory, extractorsFactory, null, null);
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

}
