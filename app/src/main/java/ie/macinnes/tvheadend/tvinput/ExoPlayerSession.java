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

public class ExoPlayerSession extends BaseSession implements ExoPlayer.EventListener {
    private static final String TAG = ExoPlayerSession.class.getName();

    private SimpleExoPlayer mExoPlayer;
    private MappingTrackSelector mTrackSelector;
    private MediaSource mMediaSource;

    public ExoPlayerSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");

        setOverlayViewEnabled(true);

        buildExoPlayer();
    }

    @Override
    protected boolean playChannel(int tvhChannelId) {
        // Stop any existing playback
        stopPlayback();

        buildMediaSource(tvhChannelId);

        mExoPlayer.prepare(mMediaSource);
        mExoPlayer.setPlayWhenReady(true);

        // TODO: Use ExoPlayer events to set video availability
        notifyVideoAvailable();

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

        MappingTrackSelector.MappedTrackInfo mappedTrackInfos = mTrackSelector.getCurrentMappedTrackInfo();

        for (int mappedTrackInfoIndex = 0; mappedTrackInfoIndex < mappedTrackInfos.length; mappedTrackInfoIndex++) {
            TrackGroupArray trackGroups = mappedTrackInfos.getTrackGroups(mappedTrackInfoIndex);

            Log.d(TAG, "Processing mappedTrackInfo: " + mappedTrackInfoIndex);

            for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                Log.d(TAG, "Processing trackGroup: " + groupIndex);
                TrackGroup trackGroup = trackGroups.get(groupIndex);

                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                    Log.d(TAG, "Processing track: " + trackIndex);
                    Format format = trackGroup.getFormat(trackIndex);

                    String candidateTrackId = (format.id == null ? groupIndex + "/" + trackIndex : format.id);

                    if (candidateTrackId.equals(trackId)) {
                        Log.d(TAG, "Found Requested Track: " + ExoPlayerUtils.buildTrackName(format));

                        for (int i = 0; i < mExoPlayer.getRendererCount(); i++) {
                            int rendererType = mExoPlayer.getRendererType(i);
                            Log.d(TAG, "Checking Renderer Type: " + rendererType);

                            if (type == TvTrackInfo.TYPE_AUDIO && rendererType == C.TRACK_TYPE_AUDIO) {
                                Log.d(TAG, "Setting Audio Track Selection Override");
                                MappingTrackSelector.SelectionOverride override = new MappingTrackSelector.SelectionOverride(new FixedTrackSelection.Factory(), groupIndex, trackIndex);
                                mTrackSelector.setSelectionOverride(i, trackGroups, override);

                                notifyTrackSelected(type, trackId);
                                return true;

                            } else if (type == TvTrackInfo.TYPE_SUBTITLE && rendererType == C.TRACK_TYPE_TEXT) {
                                if (mCaptionEnabled) {
                                    Log.d(TAG, "Setting Text Track Selection Override");
                                    MappingTrackSelector.SelectionOverride override = new MappingTrackSelector.SelectionOverride(new FixedTrackSelection.Factory(), groupIndex, trackIndex);
                                    mTrackSelector.setRendererDisabled(i, false);
                                    mTrackSelector.setSelectionOverride(i, trackGroups, override);

                                    notifyTrackSelected(type, trackId);
                                    return true;
                                } else {
                                    Log.d(TAG, "Skipping Text Track Selection Override, Captions disabled");
                                    mTrackSelector.setRendererDisabled(i, true);
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
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
        }

        // Process Available Tracks
        List<TvTrackInfo> tvTrackInfos = new ArrayList<>();

        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
            TrackGroup trackGroup = trackGroups.get(groupIndex);

            Log.d(TAG, "Processing trackGroup: " + groupIndex);

            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                Log.d(TAG, "Processing track: " + trackIndex);
                Format format = trackGroup.getFormat(trackIndex);

                String trackId = (format.id == null ? groupIndex + "/" + trackIndex : format.id);

                Log.d(TAG, "Processing track: " + ExoPlayerUtils.buildTrackName(format));

                TvTrackInfo.Builder builder;
                int trackType = MimeTypes.getTrackType(format.sampleMimeType);

                switch (trackType) {
                    case C.TRACK_TYPE_VIDEO:
                        builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, trackId);
                        if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE) {
                            builder.setVideoWidth((int)(format.width * format.pixelWidthHeightRatio));
                            builder.setVideoHeight(format.height);
                        }
                        builder.setVideoFrameRate(format.frameRate);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            builder.setVideoPixelAspectRatio(format.pixelWidthHeightRatio);
                        }
                        break;

                    case C.TRACK_TYPE_AUDIO:
                        builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, trackId);
                        builder.setAudioChannelCount(format.channelCount);
                        builder.setAudioSampleRate(format.sampleRate);
                        break;

                    case C.TRACK_TYPE_TEXT:
                        builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, trackId);
                        break;

                    default:
                        Log.e(TAG, "Unsupported track type: " + format.sampleMimeType);
                        continue;
                }

                if (!TextUtils.isEmpty(format.language) && !format.language.equals("und")) {
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
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

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

        mTrackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        LoadControl loadControl = new DefaultLoadControl();

        mExoPlayer = ExoPlayerFactory.newSimpleInstance(mContext, mTrackSelector, loadControl);
        mExoPlayer.addListener(this);
//        mExoPlayer.setTextOutput();
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
        DataSource.Factory dataSourceFactory = new ExoPlayerHttpDataSourceFactory(headers);

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
