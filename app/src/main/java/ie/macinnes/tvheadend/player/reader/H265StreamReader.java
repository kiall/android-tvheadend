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
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.video.HevcConfig;

import java.util.List;

import ie.macinnes.htsp.HtspMessage;

public class H265StreamReader extends PlainStreamReader {
    private static final String TAG = H265StreamReader.class.getName();

    public H265StreamReader(Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream) {
        List<byte[]> initializationData = null;

        if (stream.containsKey("meta")) {
            try {
                HevcConfig hevcConfig = HevcConfig.parse(new ParsableByteArray(stream.getByteArray("meta")));
                initializationData = hevcConfig.initializationData;
            } catch (ParserException e) {
                Log.e(TAG, "Failed to parse H265 meta, discarding");
            }
        }

        return Format.createVideoSampleFormat(
                Integer.toString(streamIndex),
                MimeTypes.VIDEO_H265,
                null,
                Format.NO_VALUE,
                Format.NO_VALUE,
                stream.getInteger("width"),
                stream.getInteger("height"),
                StreamReaderUtils.frameDurationToFrameRate(stream.getInteger("duration", Format.NO_VALUE)),
                initializationData,
                null);
    }

    @Override
    protected int getTrackType() {
        return C.TRACK_TYPE_VIDEO;
    }
}
