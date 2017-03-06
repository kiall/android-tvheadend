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
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.Collections;
import java.util.List;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.TvhMappings;

public class AacStreamReader extends PlainStreamReader {
    private static final String TAG = AacStreamReader.class.getName();

    public AacStreamReader(Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream) {
        List<byte[]> initializationData = null;

        int rate = Format.NO_VALUE;
        if (stream.containsKey("rate")) {
            rate = TvhMappings.sriToRate(stream.getInteger("rate"));
        }

        final int channels = stream.getInteger("channels", Format.NO_VALUE);

        if (stream.containsKey("meta")) {
            initializationData = Collections.singletonList(stream.getByteArray("meta"));
        } else {
            initializationData = Collections.singletonList(
                    CodecSpecificDataUtil.buildAacLcAudioSpecificConfig(rate, channels));
        }

        return Format.createAudioSampleFormat(
                Integer.toString(streamIndex),
                MimeTypes.AUDIO_AAC,
                null,
                Format.NO_VALUE,
                Format.NO_VALUE,
                channels,
                rate,
                C.ENCODING_PCM_16BIT,
                initializationData,
                null,
                C.SELECTION_FLAG_AUTOSELECT,
                stream.getString("language", "und")
        );
    }
}
