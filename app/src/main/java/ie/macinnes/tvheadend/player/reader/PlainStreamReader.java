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
import com.google.android.exoplayer2.util.ParsableByteArray;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.Application;

/**
 * A PlainStreamReader simply copies the raw bytes from muxpkt's over onto the track output
 */
abstract class PlainStreamReader implements StreamReader {
    private static final String TAG = PlainStreamReader.class.getName();

    private final Context mContext;
    private final int mTrackType;
    private String mStreamType;
    protected TrackOutput mTrackOutput;

    public PlainStreamReader(Context context, int trackType) {
        mContext = context;
        mTrackType = trackType;
    }

    @Override
    public final void createTracks(@NonNull HtspMessage stream, @NonNull ExtractorOutput output) {
        final int streamIndex = stream.getInteger("index");
        mStreamType = stream.getString("type");
        mTrackOutput = output.track(streamIndex, getTrackType());
        mTrackOutput.format(buildFormat(streamIndex, stream));
    }

    @Override
    public final void consume(@NonNull final HtspMessage message) {
        final long pts = message.getLong("pts");
        final int frameType = message.getInteger("frametype", -1);
        final byte[] payload = message.getByteArray("payload");

        final ParsableByteArray pba = new ParsableByteArray(payload);

        int bufferFlags = 0;

        if (mTrackType == C.TRACK_TYPE_VIDEO) {
            // We're looking at a Video stream, be picky about what frames are called keyframes

            // Type -1 = TVHeadend has not provided us a frame type, so everything "is a keyframe"
            // Type 73 = I - Intra-coded picture - Full Picture
            // Type 66 = B - Predicted picture - Depends on previous frames
            // Type 80 = P - Bidirectional predicted picture - Depends on previous+future frames
            if (frameType == -1 || frameType == 73) {
                bufferFlags |= C.BUFFER_FLAG_KEY_FRAME;
            }
        } else {
            // We're looking at a Audio / Text etc stream, consider everything a key frame
            bufferFlags |= C.BUFFER_FLAG_KEY_FRAME;
        }

        mTrackOutput.sampleData(pba, payload.length);
        mTrackOutput.sampleMetadata(pts, bufferFlags, payload.length, 0, null);
    }

    @Override
    public void release() {
        // Watch for memory leaks
        Application.getRefWatcher(mContext).watch(this);
    }

    @NonNull
    abstract protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream);

    abstract protected int getTrackType();
}
