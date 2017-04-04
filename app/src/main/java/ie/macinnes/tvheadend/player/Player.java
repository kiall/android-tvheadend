/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
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

package ie.macinnes.tvheadend.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.media.PlaybackParams;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Application;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;

public class Player implements ExoPlayer.EventListener {
    private static final String TAG = Player.class.getName();

    private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
    private static final int TEXT_UNIT_PIXELS = 0;

    public static final long INVALID_TIMESHIFT_TIME = Subscriber.INVALID_TIMESHIFT_TIME;

    public interface Listener {
        /**
         * Called when ther player state changes.
         *
         * @param playWhenReady Whether playback will proceed when ready.
         * @param playbackState One of the {@code STATE} constants defined in the {@link ExoPlayer}
         *     interface.
         */
        void onPlayerStateChanged(boolean playWhenReady, int playbackState);

        /**
         * Called when an error occurs.
         *
         * @param error The error.
         */
        void onPlayerError(ExoPlaybackException error);

        /**
         * Called when the available or selected tracks change.
         *
         * @param tracks a list of tracks
         */
        void onTracksChanged(List<TvTrackInfo> tracks, SparseArray<String> selectedTracks);
    }

    private final Context mContext;
    private final SimpleHtspConnection mConnection;
    private final Listener mListener;

    private final SharedPreferences mSharedPreferences;

    private SimpleExoPlayer mExoPlayer;
    private TvheadendTrackSelector mTrackSelector;
    private EventLogger mEventLogger;
    private HtspDataSource.Factory mDataSourceFactory;
    private ExtractorsFactory mExtractorsFactory;

    private MediaSource mMediaSource;
    private Subscriber mSubscriber;

    public Player(Context context, SimpleHtspConnection connection, Listener listener) {
        mContext = context;
        mConnection = connection;
        mListener = listener;

        mSharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        buildExoPlayer();
    }

    public void open(Uri channelUri) {
        // Stop any existing playback
        stop();

        // Create the media source
        buildHtspMediaSource(channelUri);

        // Prepare the media source
        mExoPlayer.prepare(mMediaSource);
        mExoPlayer.setPlayWhenReady(true);
    }

    public void release() {
        // Stop any existing playback
        stop();

        // Release ExoPlayer
        mExoPlayer.removeListener(this);
        mExoPlayer.release();
    }

    public void setSurface(Surface surface) {
        mExoPlayer.setVideoSurface(surface);
    }

    public void setVolume(float volume) {
        mExoPlayer.setVolume(volume);
    }

    public boolean selectTrack(int type, String trackId) {
        return mTrackSelector.selectTrack(type, trackId);
    }

    public void resume() {
        if (mSubscriber != null) {
            Log.d(TAG, "Resuming Subscriber");
            mSubscriber.resume();
        }

        mExoPlayer.setPlayWhenReady(true);
    }

    public void pause() {
        mExoPlayer.setPlayWhenReady(false);

        if (mSubscriber != null) {
            Log.d(TAG, "Pausing Subscriber");
            mSubscriber.pause();
        }
    }

    public void seek(long timeMs) {
        Log.d(TAG, "seek to time: " + timeMs);

        long seekPts = (timeMs * 1000) - mSubscriber.getTimeshiftStartTime();
        seekPts = Math.max(seekPts, mSubscriber.getTimeshiftStartPts());

        mExoPlayer.seekTo(seekPts / 1000);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setPlaybackParams(PlaybackParams params) {
        float speed = params.getSpeed();
        int translatedSpeed;

        if (speed == 0.0) {
            translatedSpeed = 100;
        } else if (speed == -2.0) {
            translatedSpeed = -200;
        } else if (speed == -4.0) {
            translatedSpeed = -300;
        } else if (speed == -12.0) {
            translatedSpeed = -400;
        } else if (speed == -48.0) {
            translatedSpeed = -500;
        } else if (speed == 2.0) {
            translatedSpeed = 200;
        } else if (speed == 4.0) {
            translatedSpeed = 300;
        } else if (speed == 12.0) {
            translatedSpeed = 400;
        } else if (speed == 48.0) {
            translatedSpeed = 500;
        } else {
            Log.d(TAG, "Unknown speed??? " + speed);
            return;
        }

        Log.d(TAG, "Speed: " + params.getSpeed() + " / " + translatedSpeed);

        if (mSubscriber != null) {
            mSubscriber.setSpeed(translatedSpeed);
            mExoPlayer.setPlaybackParams(new PlaybackParams().setSpeed(speed));
        }
    }

    public void stop() {
        mExoPlayer.stop();
        mTrackSelector.clearSelectionOverrides();

        if (mMediaSource != null) {
            mMediaSource.releaseSource();

            // Watch for memory leaks
            Application.getRefWatcher(mContext).watch(mMediaSource);
        }

        mSubscriber = null;
    }

    public long getTimeshiftStartPosition() {
        if (mSubscriber != null) {
            long startTime = mSubscriber.getTimeshiftStartTime();
            if (startTime != Subscriber.INVALID_TIMESHIFT_TIME) {
                return startTime / 1000;
            }
        }

        return INVALID_TIMESHIFT_TIME;
    }

    public long getTimeshiftCurrentPosition() {
        if (mSubscriber != null) {
            long offset = mSubscriber.getTimeshiftOffsetPts();
            if (offset != Subscriber.INVALID_TIMESHIFT_TIME) {
                return System.currentTimeMillis() + (offset / 1000);
            }
        }

        return INVALID_TIMESHIFT_TIME;
    }

    public View getSubtitleView(CaptioningManager.CaptionStyle captionStyle, float fontScale) {
        SubtitleView view = new SubtitleView(mContext);

        CaptionStyleCompat captionStyleCompat = CaptionStyleCompat.createFromCaptionStyle(captionStyle);

        float captionTextSize = getCaptionFontSize();
        captionTextSize *= fontScale;

        final boolean applyEmbeddedStyles = mSharedPreferences.getBoolean(
                Constants.KEY_CAPTIONS_APPLY_EMBEDDED_STYLES,
                mContext.getResources().getBoolean(R.bool.pref_default_captions_apply_embedded_styles)
        );

        view.setStyle(captionStyleCompat);
        view.setVisibility(View.VISIBLE);
        view.setFixedTextSize(TEXT_UNIT_PIXELS, captionTextSize);
        view.setApplyEmbeddedStyles(applyEmbeddedStyles);

        mExoPlayer.setTextOutput(view);

        return view;
    }

    // Misc Internal Methods
    private void buildExoPlayer() {
        TrackSelection.Factory trackSelectionFactory =
                new AdaptiveTrackSelection.Factory(null);

        mTrackSelector = new TvheadendTrackSelector(trackSelectionFactory);

        LoadControl loadControl = buildLoadControl();

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

        final String streamProfile = mSharedPreferences.getString(
                Constants.KEY_HTSP_STREAM_PROFILE,
                mContext.getResources().getString(R.string.pref_default_htsp_stream_profile)
        );

        // Produces DataSource instances through which media data is loaded.
        mDataSourceFactory = new HtspDataSource.Factory(mContext, mConnection, streamProfile);

        // Produces Extractor instances for parsing the media data.
        mExtractorsFactory = new HtspExtractor.Factory(mContext);
    }

    private LoadControl buildLoadControl() {
        int bufferForPlaybackMs = Integer.parseInt(
                mSharedPreferences.getString(
                        Constants.KEY_BUFFER_PLAYBACK_MS,
                        mContext.getResources().getString(R.string.pref_default_buffer_playback_ms)
                )
        );

        return new DefaultLoadControl(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                bufferForPlaybackMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        );
    }

    private void buildHtspMediaSource(Uri channelUri) {
        // This is the MediaSource representing the media to be played.
        mMediaSource = new ExtractorMediaSource(channelUri,
                mDataSourceFactory, mExtractorsFactory, null, mEventLogger);
    }

    private float getCaptionFontSize() {
        Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);
        return Math.max(mContext.getResources().getDimension(R.dimen.subtitle_minimum_font_size),
                CAPTION_LINE_HEIGHT_RATIO * Math.min(displaySize.x, displaySize.y));
    }

    private boolean getTrackStatusBoolean(TrackSelection selection, TrackGroup group,
                                          int trackIndex) {
        return selection != null && selection.getTrackGroup() == group
                && selection.indexOf(trackIndex) != C.INDEX_UNSET;
    }

    // ExoPlayer.EventListener Methods
    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = mTrackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) {
            return;
        }

        // Process Tracks
        List<TvTrackInfo> tracks = new ArrayList<>();
        SparseArray<String> selectedTracks = new SparseArray<>();

        for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.length; rendererIndex++) {
            TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            TrackSelection trackSelection = trackSelections.get(rendererIndex);
            if (rendererTrackGroups.length > 0) {
                for (int groupIndex = 0; groupIndex < rendererTrackGroups.length; groupIndex++) {
                    TrackGroup trackGroup = rendererTrackGroups.get(groupIndex);
                    for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                        int formatSupport = mappedTrackInfo.getTrackFormatSupport(rendererIndex, groupIndex, trackIndex);

                        if (formatSupport == RendererCapabilities.FORMAT_HANDLED) {
                            Format format = trackGroup.getFormat(trackIndex);
                            TvTrackInfo tvTrackInfo = ExoPlayerUtils.buildTvTrackInfo(format);

                            if (tvTrackInfo != null) {
                                tracks.add(tvTrackInfo);

                                Boolean selected = getTrackStatusBoolean(trackSelection, trackGroup, trackIndex);

                                if (selected) {
                                    int trackType = MimeTypes.getTrackType(format.sampleMimeType);

                                    switch (trackType) {
                                        case C.TRACK_TYPE_VIDEO:
                                            selectedTracks.put(TvTrackInfo.TYPE_VIDEO, format.id);
                                            break;
                                        case C.TRACK_TYPE_AUDIO:
                                            selectedTracks.put(TvTrackInfo.TYPE_AUDIO, format.id);
                                            break;
                                        case C.TRACK_TYPE_TEXT:
                                            selectedTracks.put(TvTrackInfo.TYPE_SUBTITLE, format.id);
                                            break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        mListener.onTracksChanged(tracks, selectedTracks);
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (isLoading) {
            // Fetch the Subscriber for later use
            mSubscriber = mDataSourceFactory.getMostRecentDataSource().getSubscriber();
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        mListener.onPlayerStateChanged(playWhenReady, playbackState);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        mListener.onPlayerError(error);
    }

    @Override
    public void onPositionDiscontinuity() {

    }
}
