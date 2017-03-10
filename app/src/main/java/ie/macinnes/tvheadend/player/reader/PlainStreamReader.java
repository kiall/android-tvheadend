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
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.Application;

/**
 * A PlainStreamReader simply copies the raw bytes from muxpkt's over onto the track output
 */
abstract class PlainStreamReader implements StreamReader {
    private final Context mContext;
    protected TrackOutput mTrackOutput;

    public PlainStreamReader(Context context) {
        mContext = context;
    }

    @Override
    public final void createTracks(@NonNull HtspMessage stream, @NonNull ExtractorOutput output) {
        final int streamIndex = stream.getInteger("index");
        mTrackOutput = output.track(streamIndex);
        mTrackOutput.format(buildFormat(streamIndex, stream));
    }

    @Override
    public final void consume(@NonNull final HtspMessage message) {
        final long pts = message.getLong("pts");
        final byte[] payload = message.getByteArray("payload");

        final ParsableByteArray pba = new ParsableByteArray(payload);

        // TODO: Set Buffer Flag key frame based on frametype
        // frametype   u32   required   Type of frame as ASCII value: 'I', 'P', 'B'
        mTrackOutput.sampleData(pba, payload.length);
        mTrackOutput.sampleMetadata(pts, C.BUFFER_FLAG_KEY_FRAME, payload.length, 0, null);
    }

    @Override
    public void release() {
        // Watch for memory leaks
        Application.getRefWatcher(mContext).watch(this);
    }

    @NonNull
    abstract protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream);
}
