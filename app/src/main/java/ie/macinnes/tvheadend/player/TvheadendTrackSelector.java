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

package ie.macinnes.tvheadend.player;

import android.media.tv.TvTrackInfo;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;


public class TvheadendTrackSelector extends DefaultTrackSelector {
    private static final String TAG = TvheadendTrackSelector.class.getName();

    private String mVideoTrackId;
    private String mAudioTrackId;
    private String mSubtitleTrackId;

    public TvheadendTrackSelector() {
    }

    public TvheadendTrackSelector(TrackSelection.Factory adaptiveVideoTrackSelectionFactory) {
        super(adaptiveVideoTrackSelectionFactory);
    }

    public boolean onSelectTrack(int type, String trackId) {
        Log.d(TAG, "TrackSelector onSelectTrack: " + type + " / " + trackId);

        switch (type) {
            case TvTrackInfo.TYPE_VIDEO:
                mVideoTrackId = trackId;
                break;
            case TvTrackInfo.TYPE_AUDIO:
                mAudioTrackId = trackId;
                break;
            case TvTrackInfo.TYPE_SUBTITLE:
                mSubtitleTrackId = trackId;
                break;
            default:
                throw new RuntimeException("Invalid track type: " + type);
        }

        invalidate();

        return true;
    }

    protected TrackSelection selectVideoTrack(
            RendererCapabilities rendererCapabilities, TrackGroupArray groups, int[][] formatSupport,
            int maxVideoWidth, int maxVideoHeight, boolean allowNonSeamlessAdaptiveness,
            boolean allowMixedMimeAdaptiveness, int viewportWidth, int viewportHeight,
            boolean orientationMayChange, TrackSelection.Factory adaptiveVideoTrackSelectionFactory,
            boolean exceedConstraintsIfNecessary, boolean exceedRendererCapabilitiesIfNecessary)
            throws ExoPlaybackException {
        Log.d(TAG, "TrackSelector selectVideoTrack");

        // If we haven't explicitly chosen a track, defer to the DefaultTrackSelector implementation.
        if (mVideoTrackId == null) {
            return super.selectVideoTrack(rendererCapabilities, groups, formatSupport, maxVideoWidth, maxVideoHeight,
                    allowNonSeamlessAdaptiveness, allowMixedMimeAdaptiveness, viewportWidth, viewportHeight,
                    orientationMayChange, adaptiveVideoTrackSelectionFactory, exceedConstraintsIfNecessary,
                    exceedRendererCapabilitiesIfNecessary);
        } else {
            for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                TrackGroup trackGroup = groups.get(groupIndex);
                int[] trackFormatSupport = formatSupport[groupIndex];

                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                    if (isSupported(trackFormatSupport[trackIndex], false)) {
                        Format format = trackGroup.getFormat(trackIndex);

                        if (mVideoTrackId.equals(format.id)) {
                            return new FixedTrackSelection(trackGroup, trackIndex);
                        }
                    }
                }
            }

            return null;
        }
    }

    protected TrackSelection selectAudioTrack(
            TrackGroupArray groups, int[][] formatSupport, String preferredAudioLanguage,
            boolean exceedRendererCapabilitiesIfNecessary) {
        Log.d(TAG, "TrackSelector selectAudioTrack");

        // If we haven't explicitly chosen a track, defer to the DefaultTrackSelector implementation.
        if (mAudioTrackId == null) {
            return super.selectAudioTrack(groups, formatSupport, preferredAudioLanguage, exceedRendererCapabilitiesIfNecessary);
        } else {
            for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                TrackGroup trackGroup = groups.get(groupIndex);
                int[] trackFormatSupport = formatSupport[groupIndex];

                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                    if (isSupported(trackFormatSupport[trackIndex], false)) {
                        Format format = trackGroup.getFormat(trackIndex);

                        if (mAudioTrackId.equals(format.id)) {
                            Log.d(TAG, "Matched audio track, create FixedTrackSelection");
                            return new FixedTrackSelection(trackGroup, trackIndex);
                        }
                    }
                }
            }

            return null;
        }
    }

    protected TrackSelection selectTextTrack(
            TrackGroupArray groups, int[][] formatSupport, String preferredTextLanguage,
            String preferredAudioLanguage, boolean exceedRendererCapabilitiesIfNecessary) {
        Log.d(TAG, "TrackSelector selectTextTrack");

        // If we haven't explicitly chosen a track, defer to the DefaultTrackSelector implementation.
        if (mSubtitleTrackId == null) {
            return super.selectTextTrack(groups, formatSupport, preferredTextLanguage, preferredAudioLanguage, exceedRendererCapabilitiesIfNecessary);
        } else {
            for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                TrackGroup trackGroup = groups.get(groupIndex);
                int[] trackFormatSupport = formatSupport[groupIndex];

                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                    if (isSupported(trackFormatSupport[trackIndex], false)) {
                        Format format = trackGroup.getFormat(trackIndex);

                        if (mSubtitleTrackId.equals(format.id)) {
                            return new FixedTrackSelection(trackGroup, trackIndex);
                        }
                    }
                }
            }

            return null;
        }
    }
}
