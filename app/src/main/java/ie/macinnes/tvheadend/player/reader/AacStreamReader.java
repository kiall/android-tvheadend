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
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.Collections;
import java.util.List;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.TvhMappings;

// See https://wiki.multimedia.cx/index.php?title=ADTS

public class AacStreamReader implements StreamReader {
    private static final String TAG = AacStreamReader.class.getName();

    private static final int ADTS_HEADER_SIZE = 7;
    private static final int ADTS_CRC_SIZE = 2;

    private final Context mContext;
    protected TrackOutput mTrackOutput;

    public AacStreamReader(Context context) {
        mContext = context;
    }

    @Override
    public void createTracks(HtspMessage stream, ExtractorOutput output) {
        final int streamIndex = stream.getInteger("index");
        mTrackOutput = output.track(streamIndex);
        mTrackOutput.format(buildFormat(streamIndex, stream));
    }

    @Override
    public void consume(@NonNull HtspMessage message) {
        final long pts = message.getLong("pts");
        final byte[] payload = message.getByteArray("payload");

        final ParsableByteArray pba = new ParsableByteArray(payload);

        int skipLength;

        if (hasCrc(payload[1])) {
            // Have a CRC
            skipLength = ADTS_HEADER_SIZE + ADTS_CRC_SIZE;
        } else {
            // No CRC
            skipLength = ADTS_HEADER_SIZE;
        }

        pba.skipBytes(skipLength);

        final int aacFrameLength = payload.length - skipLength;

        // TODO: Set Buffer Flag key frame based on frametype
        // frametype   u32   required   Type of frame as ASCII value: 'I', 'P', 'B'
        mTrackOutput.sampleData(pba, aacFrameLength);
        mTrackOutput.sampleMetadata(pts, C.BUFFER_FLAG_KEY_FRAME, aacFrameLength, 0, null);
    }

    @Override
    public void release() {

    }

    @NonNull
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

    private boolean hasCrc(byte b) {
        int data = b & 0xFF;
        return (data & 0x1) == 0;
    }
}
