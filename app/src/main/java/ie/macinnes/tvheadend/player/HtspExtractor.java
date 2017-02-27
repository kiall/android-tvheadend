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

import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.player.reader.StreamReader;
import ie.macinnes.tvheadend.player.reader.StreamReadersFactory;


public class HtspExtractor implements Extractor {
    private static final String TAG = HtspExtractor.class.getName();

    public static class Factory implements ExtractorsFactory {
        private static final String TAG = Factory.class.getName();

        public Factory() {
        }

        @Override
        public Extractor[] createExtractors() {
            return new Extractor[] { new HtspExtractor() };
        }
    }

    private class HtspSeekMap implements SeekMap {
        @Override
        public boolean isSeekable() {
            return false;
        }

        @Override
        public long getDurationUs() {
            return C.TIME_UNSET;
        }

        @Override
        public long getPosition(long timeUs) {
            return 0;
        }
    }

    private ExtractorOutput mOutput;
    private SparseArray<StreamReader> mStreamReaders = new SparseArray<>();

    public HtspExtractor() {
        Log.d(TAG, "New HtspExtractor instantiated");
    }

    // Extractor Methods
    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return true;
    }

    @Override
    public void init(ExtractorOutput output) {
        mOutput = output;
        mOutput.seekMap(new HtspSeekMap());
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        byte[] rawBytes = new byte[1024 * 1024];

        int bytesRead = input.read(rawBytes, 0, rawBytes.length);

        if (Constants.DEBUG) {
            Log.v(TAG, "Read " + bytesRead + " bytes");
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawBytes, 0, bytesRead);
        ObjectInput objectInput = null;

        try {
            while (inputStream.available() > 0) {
                objectInput = new ObjectInputStream(inputStream);
                handleMessage((HtspMessage) objectInput.readObject());
            }
        } catch (IOException e) {
            // Ignore?
            Log.w(TAG, "IOException", e);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Class Not Found");
        } finally {
            try {
                if (objectInput != null) {
                    objectInput.close();
                }
            } catch (IOException ex) {
                // Ignore
            }
        }

        return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {

    }

    @Override
    public void release() {

    }

    // Internal Methods
    private void handleMessage(@NonNull final HtspMessage message) {
        final String method = message.getString("method");

        if (method.equals("subscriptionStart")) {
            handleSubscriptionStart(message);
        } else if (method.equals("muxpkt")) {
            handleMuxpkt(message);
        }
    }

    private void handleSubscriptionStart(@NonNull final HtspMessage message) {
        Log.i(TAG, "Handling Subscription Start");

        StreamReadersFactory streamReadersFactory = new StreamReadersFactory();

        for (HtspMessage stream : message.getHtspMessageArray("streams")) {
            final int streamIndex = stream.getInteger("index");
            final String streamType = stream.getString("type");

            Log.d(TAG, "Creating StreamReader for " + streamType + " stream at index " + streamIndex);

            final StreamReader streamReader = streamReadersFactory.createStreamReader(streamType);
            if (streamReader != null) {
                streamReader.createTracks(stream, mOutput);
                mStreamReaders.put(streamIndex, streamReader);
            }
        }

        Log.d(TAG, "All streams have now been handled");
        mOutput.endTracks();
    }

    private void handleMuxpkt(@NonNull final HtspMessage message) {
//        subscriptionId     u32   required   Subscription ID.
//        frametype          u32   required   Type of frame as ASCII value: 'I', 'P', 'B'
//        stream             u32   required   Stream index. Corresponds to the streams reported in the subscriptionStart message.
//        dts                s64   optional   Decode Time Stamp in µs.
//        pts                s64   optional   Presentation Time Stamp in µs.
//        duration           u32   required   Duration of frame in µs.
//        payload            bin   required   Actual frame data.

        final int streamIndex = message.getInteger("stream");

        final StreamReader streamReader = mStreamReaders.get(streamIndex);
        if (streamReader == null) {
            // Not a stream we care about, move on.
            return;
        }

        streamReader.consume(message);
    }
}
