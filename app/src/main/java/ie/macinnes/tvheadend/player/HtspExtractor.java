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

import android.util.Log;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;

import java.io.IOException;
import java.nio.ByteBuffer;


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

    private ExtractorOutput mOutput;
    private ByteBuffer mBuffer;

    public HtspExtractor() {
        mBuffer = ByteBuffer.allocate(10240);
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return true;
    }

    @Override
    public void init(ExtractorOutput output) {
        mOutput = output;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        ByteBuffer messageTypeBuffer = ByteBuffer.allocate(2);
        input.readFully(messageTypeBuffer.array(), 0, 2);
        messageTypeBuffer.position(0);

//        Log.d(TAG, "Read " + bytesRead);

//        if (bytesRead == 0) {
//            Log.d(TAG, "Nothing Read");
//            return RESULT_CONTINUE;
//        }
//
//        if (bytesRead != 2) {
//            Log.d(TAG, "Didn't read enough bytes. DERP");
//
//            return RESULT_CONTINUE;
//        }

        short messageType = messageTypeBuffer.getShort();

        if (messageType == HtspDataSource.MSG_TYPE_MUXPKT) {
            Log.w(TAG, "HtspExtractor - MSG_TYPE_MUXPKT");
//            ByteBuffer messageBuffer = ByteBuffer.allocate(8);
//            bytesRead = input.read(messageBuffer.array(), 0, 8);
////            input.skip(bytesRead);
//
//            int stream = messageBuffer.getInt();
//            int payloadLength = messageBuffer.getInt();
//
//            mOutput.track(stream).sampleData(input, payloadLength, false);
        } else if (messageType == HtspDataSource.MSG_TYPE_SUBSCRIPTION_START) {
            Log.w(TAG, "HtspExtractor - MSG_TYPE_SUBSCRIPTION_START");
        } else if (messageType == HtspDataSource.MSG_TYPE_SUBSCRIPTION_STATUS) {
            Log.w(TAG, "HtspExtractor - MSG_TYPE_SUBSCRIPTION_STATUS");
        } else {
            Log.w(TAG, "HtspExtractor - Unknown Message Type: " + messageType);
        }

        return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {

    }

    @Override
    public void release() {

    }
}
