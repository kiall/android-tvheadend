/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
 * Copyright (C) 2016 The Android Open Source Project
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
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.TvhMappings;

public class VorbisStreamReader extends PlainStreamReader {
    private static final String TAG = VorbisStreamReader.class.getName();

    public VorbisStreamReader(Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream) {
        List<byte[]> initializationData = null;

        if (stream.containsKey("meta")) {
            try {
                initializationData = parseVorbisCodecPrivate(stream.getByteArray("meta"));
            } catch (ParserException e) {
                Log.e(TAG, "Failed to parse Vorbis meta, discarding");
            }
        }

        int rate = Format.NO_VALUE;
        if (stream.containsKey("rate")) {
            rate = TvhMappings.sriToRate(stream.getInteger("rate"));
        }

        return Format.createAudioSampleFormat(
                Integer.toString(streamIndex),
                MimeTypes.AUDIO_VORBIS,
                null,
                Format.NO_VALUE,
                Format.NO_VALUE,
                stream.getInteger("channels", Format.NO_VALUE),
                rate,
                C.ENCODING_PCM_16BIT,
                initializationData,
                null,
                C.SELECTION_FLAG_AUTOSELECT,
                stream.getString("language", "und")
        );
    }

    /**
     * Builds initialization data for a {@link Format} from Vorbis codec private data.
     *
     * @return The initialization data for the {@link Format}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static List<byte[]> parseVorbisCodecPrivate(byte[] codecPrivate)
            throws ParserException {
        try {
            if (codecPrivate[0] != 0x02) {
                throw new ParserException("Error parsing vorbis codec private");
            }
            int offset = 1;
            int vorbisInfoLength = 0;
            while (codecPrivate[offset] == (byte) 0xFF) {
                vorbisInfoLength += 0xFF;
                offset++;
            }
            vorbisInfoLength += codecPrivate[offset++];

            int vorbisSkipLength = 0;
            while (codecPrivate[offset] == (byte) 0xFF) {
                vorbisSkipLength += 0xFF;
                offset++;
            }
            vorbisSkipLength += codecPrivate[offset++];

            if (codecPrivate[offset] != 0x01) {
                throw new ParserException("Error parsing vorbis codec private");
            }
            byte[] vorbisInfo = new byte[vorbisInfoLength];
            System.arraycopy(codecPrivate, offset, vorbisInfo, 0, vorbisInfoLength);
            offset += vorbisInfoLength;
            if (codecPrivate[offset] != 0x03) {
                throw new ParserException("Error parsing vorbis codec private");
            }
            offset += vorbisSkipLength;
            if (codecPrivate[offset] != 0x05) {
                throw new ParserException("Error parsing vorbis codec private");
            }
            byte[] vorbisBooks = new byte[codecPrivate.length - offset];
            System.arraycopy(codecPrivate, offset, vorbisBooks, 0, codecPrivate.length - offset);
            List<byte[]> initializationData = new ArrayList<>(2);
            initializationData.add(vorbisInfo);
            initializationData.add(vorbisBooks);
            return initializationData;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ParserException("Error parsing vorbis codec private");
        }
    }

}
