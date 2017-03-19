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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ie.macinnes.htsp.HtspMessage;

public class DvbsubStreamReader extends PlainStreamReader {
    private static final String TAG = DvbsubStreamReader.class.getName();

    public DvbsubStreamReader(Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected Format buildFormat(int streamIndex, @NonNull HtspMessage stream) {
        final int compositionId = stream.getInteger("composition_id");
        final int ancillaryId = stream.getInteger("ancillary_id");

        List<byte[]> initializationData = Collections.singletonList(new byte[] {
                (byte) ((compositionId >> 8) & 0xFF),
                (byte) ((compositionId) & 0xFF),
                (byte) ((ancillaryId >> 8) & 0xFF),
                (byte) ((ancillaryId) & 0xFF)
        });

        return Format.createImageSampleFormat(
                Integer.toString(streamIndex),
                MimeTypes.APPLICATION_DVBSUBS,
                null,
                Format.NO_VALUE,
                initializationData,
                stream.getString("language", "und"),
                null);
    }

    @Override
    protected int getTrackType() {
        return C.TRACK_TYPE_TEXT;
    }
}
