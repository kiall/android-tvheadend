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

package ie.macinnes.tvheadend.player.reader;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.TvhMappings;

public class Mpeg2AudioStreamReader extends PlainStreamReader {

    public Mpeg2AudioStreamReader(Context context) {
        super(context, C.TRACK_TYPE_AUDIO);
    }

    @NonNull
    @Override
    protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream) {
        int rate = Format.NO_VALUE;
        if (stream.containsKey("rate")) {
            rate = TvhMappings.sriToRate(stream.getInteger("rate"));
        }

        // TVHeadend calls all MPEG Audio MPEG2AUDIO - e.g. it could be either mp2 or mp3 audio. We
        // need to use the new audio_version field (4.1.2498+ only). Default to mp2 as that's most
        // common for DVB.
        int audioVersion = 2;

        if (stream.containsKey("audio_version")) {
            audioVersion = stream.getInteger("audio_version");
        }

        String mimeType;

        switch (audioVersion) {
            case 1: // MP1 Audio - V.Unlikely these days
                mimeType = MimeTypes.AUDIO_MPEG_L1;
                break;
            case 2: // MP2 Audio - Pretty common in DVB streams
                mimeType = MimeTypes.AUDIO_MPEG_L2;
                break;
            case 3: // MP3 Audio - Pretty common in IPTV streams
                mimeType = MimeTypes.AUDIO_MPEG;
                break;
            default:
                throw new RuntimeException("Unknown MPEG Audio Version: " + audioVersion);
        }

        return Format.createAudioSampleFormat(
                Integer.toString(streamIndex),
                mimeType,
                null,
                Format.NO_VALUE,
                Format.NO_VALUE,
                stream.getInteger("channels", Format.NO_VALUE),
                rate,
                C.ENCODING_PCM_16BIT,
                null,
                null,
                C.SELECTION_FLAG_AUTOSELECT,
                stream.getString("language", "und")
        );
    }

    @Override
    protected int getTrackType() {
        return C.TRACK_TYPE_AUDIO;
    }
}
