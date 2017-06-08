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


import android.media.tv.TvTrackInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.Locale;

public class ExoPlayerUtils {
    private static final String TAG = ExoPlayerUtils.class.getName();

    private ExoPlayerUtils() {
        throw new IllegalAccessError("Utility class");
    }

    public static TvTrackInfo buildTvTrackInfo(Format format) {
        String trackName = ExoPlayerUtils.buildTrackName(format);
        Log.d(TAG, "Processing track: " + trackName);

        if (format.id == null) {
            Log.e(TAG, "Track ID invalid, skipping track " + trackName);
            return null;
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
                Log.w(TAG, "Unsupported track type: " + format.sampleMimeType + " / "  + trackName);
                return null;
        }

        if (!TextUtils.isEmpty(format.language)
                && !format.language.equals("und")
                && !format.language.equals("nar")
                && !format.language.equals("syn")
                && !format.language.equals("mis")) {
            builder.setLanguage(format.language);
        }

        // TODO: Determine where the Description is used..
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            builder.setDescription(ExoPlayerUtils.buildTrackName(format));
//        }

        return builder.build();
    }

    // Track name construction.
    private static String buildTrackName(Format format) {
        String trackName;
        if (MimeTypes.isVideo(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(buildResolutionString(format),
                    buildBitrateString(format)), buildTrackIdString(format));
        } else if (MimeTypes.isAudio(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                    buildAudioPropertyString(format)), buildBitrateString(format)),
                    buildTrackIdString(format));
        } else {
            trackName = joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                    buildBitrateString(format)), buildTrackIdString(format));
        }
        return trackName.length() == 0 ? "unknown" : trackName;
    }

    private static String buildResolutionString(Format format) {
        return format.width == Format.NO_VALUE || format.height == Format.NO_VALUE
                ? "" : format.width + "x" + format.height;
    }

    private static String buildAudioPropertyString(Format format) {
        return format.channelCount == Format.NO_VALUE || format.sampleRate == Format.NO_VALUE
                ? "" : format.channelCount + "ch, " + format.sampleRate + "Hz";
    }

    private static String buildLanguageString(Format format) {
        return TextUtils.isEmpty(format.language) || "und".equals(format.language) ? ""
                : format.language;
    }

    private static String buildBitrateString(Format format) {
        return format.bitrate == Format.NO_VALUE ? ""
                : String.format(Locale.US, "%.2fMbit", format.bitrate / 1000000f);
    }

    private static String buildTrackIdString(Format format) {
        return format.id == null ? "" : ("id:" + format.id);
    }

    private static String joinWithSeparator(String first, String second) {
        return first.length() == 0 ? second : (second.length() == 0 ? first : first + ", " + second);
    }
}
