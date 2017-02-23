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

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

import java.util.Arrays;
import java.util.Locale;

import ie.macinnes.htsp.HtspMessage;

public class TextsubStreamReader implements StreamReader {
    private static final String TAG = TextsubStreamReader.class.getName();

    /**
     * A template for the prefix that must be added to each subrip sample. The 12 byte end timecode
     * starting at {@link #SUBRIP_PREFIX_END_TIMECODE_OFFSET} is set to a dummy value, and must be
     * replaced with the duration of the subtitle.
     * <p>
     * Equivalent to the UTF-8 string: "1\n00:00:00,000 --> 00:00:00,000\n".
     */
    private static final byte[] SUBRIP_PREFIX = new byte[] {49, 10, 48, 48, 58, 48, 48, 58, 48, 48,
            44, 48, 48, 48, 32, 45, 45, 62, 32, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 48, 48, 10};
    /**
     * A special end timecode indicating that a subtitle should be displayed until the next subtitle,
     * or until the end of the media in the case of the last subtitle.
     * <p>
     * Equivalent to the UTF-8 string: "            ".
     */
    private static final byte[] SUBRIP_TIMECODE_EMPTY =
            new byte[] {32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32};
    /**
     * The byte offset of the end timecode in {@link #SUBRIP_PREFIX}.
     */
    private static final int SUBRIP_PREFIX_END_TIMECODE_OFFSET = 19;
    /**
     * The length in bytes of a timecode in a subrip prefix.
     */
    private static final int SUBRIP_TIMECODE_LENGTH = 12;

    protected TrackOutput mTrackOutput;

    @Override
    public final void createTracks(@NonNull HtspMessage stream, @NonNull ExtractorOutput output) {
        final int streamIndex = stream.getInteger("index");
        mTrackOutput = output.track(streamIndex);
        mTrackOutput.format(buildFormat(streamIndex, stream));
    }

    @Override
    public void consume(@NonNull final HtspMessage message) {
        final long pts = message.getInteger("pts");
        final long duration = message.getInteger("duration");
        final byte[] payload = message.getByteArray("payload");

        final int lengthWithPrefix = SUBRIP_PREFIX.length + payload.length;
        final byte[] subsipSample = Arrays.copyOf(SUBRIP_PREFIX, lengthWithPrefix);

        System.arraycopy(payload, 0, subsipSample, SUBRIP_PREFIX.length, payload.length);

        setSubripSampleEndTimecode(subsipSample, duration);

        mTrackOutput.sampleData(new ParsableByteArray(subsipSample), lengthWithPrefix);
        mTrackOutput.sampleMetadata(pts, C.BUFFER_FLAG_KEY_FRAME, lengthWithPrefix, 0, null);
    }

    @NonNull
    protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream) {
        return Format.createTextSampleFormat(
                Integer.toString(streamIndex),
                MimeTypes.APPLICATION_SUBRIP,
                null,
                Format.NO_VALUE,
                C.SELECTION_FLAG_AUTOSELECT,
                stream.getString("language", "und"),
                null
        );
    }

    private static void setSubripSampleEndTimecode(byte[] subripSample, long timeUs) {
        byte[] timeCodeData;
        if (timeUs == C.TIME_UNSET || timeUs == 0) {
            timeCodeData = SUBRIP_TIMECODE_EMPTY;
        } else {
            int hours = (int) (timeUs / 3600000000L);
            timeUs -= (hours * 3600000000L);
            int minutes = (int) (timeUs / 60000000);
            timeUs -= (minutes * 60000000);
            int seconds = (int) (timeUs / 1000000);
            timeUs -= (seconds * 1000000);
            int milliseconds = (int) (timeUs / 1000);
            timeCodeData = Util.getUtf8Bytes(String.format(Locale.US, "%02d:%02d:%02d,%03d", hours,
                    minutes, seconds, milliseconds));
        }

        System.arraycopy(timeCodeData, 0, subripSample, SUBRIP_PREFIX_END_TIMECODE_OFFSET,
                SUBRIP_TIMECODE_LENGTH);
    }
}
