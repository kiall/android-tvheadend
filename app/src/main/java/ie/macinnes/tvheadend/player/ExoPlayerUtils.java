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
import android.media.tv.TvTrackInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.Locale;

import ie.macinnes.tvheadend.R;

public class ExoPlayerUtils {
    private static final String TAG = ExoPlayerUtils.class.getName();

    public static TvTrackInfo buildTvTrackInfo(Format format, Context context) {
        String trackName = ExoPlayerUtils.buildTrackName(format, context);
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
    private static String buildTrackName(Format format, Context context) {
        String trackName;
        if (MimeTypes.isVideo(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(buildResolutionString(format, context),
                    buildBitrateString(format)), buildTrackIdString(format, context));
        } else if (MimeTypes.isAudio(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                    buildAudioPropertyString(format, context)), buildBitrateString(format)),
                    buildTrackIdString(format, context));
        } else {
            trackName = joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                    buildBitrateString(format)), buildTrackIdString(format, context));
        }
        return trackName.length() == 0 ? context.getString(R.string.track_name_unknown) : trackName;
    }

    private static String buildResolutionString(Format format, Context context) {
        return format.width == Format.NO_VALUE || format.height == Format.NO_VALUE
                ? "" : format.width + context.getString(R.string.resolution_separator) + format.height;
    }

    private static String buildAudioPropertyString(Format format, Context context) {
        return format.channelCount == Format.NO_VALUE || format.sampleRate == Format.NO_VALUE
                ? "" : context.getString(R.string.audio_format, format.channelCount, format.sampleRate);
    }

    private static String buildLanguageString(Format format) {
        return TextUtils.isEmpty(format.language) || "und".equals(format.language) ? ""
                : format.language;
    }

    private static String buildBitrateString(Format format) {
        return format.bitrate == Format.NO_VALUE ? ""
                : String.format(Locale.getDefault(), "%.2fMbit", format.bitrate / 1000000f);
    }

    private static String buildTrackIdString(Format format, Context context) {
        return format.id == null ? "" : (context.getString(R.string.id_prefix) + format.id);
    }

    private static String joinWithSeparator(String first, String second) {
        return first.length() == 0 ? second : (second.length() == 0 ? first : first + ", " + second);
    }
}
