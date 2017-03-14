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

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Application;
import ie.macinnes.tvheadend.Constants;

public class HtspDataSource implements DataSource, Subscriber.Listener, Closeable {
    private static final String TAG = HtspDataSource.class.getName();

    public static class Factory implements DataSource.Factory {
        private static final String TAG = Factory.class.getName();

        private final Context mContext;
        private final SimpleHtspConnection mConnection;
        private final String mStreamProfile;

        public Factory(Context context, SimpleHtspConnection connection, String streamProfile) {
            mContext = context;
            mConnection = connection;
            mStreamProfile = streamProfile;
        }

        @Override
        public HtspDataSource createDataSource() {
            return new HtspDataSource(mContext, mConnection, mStreamProfile);
        }
    }

    private Context mContext;
    private SimpleHtspConnection mConnection;
    private String mStreamProfile;

    private Subscriber mSubscriber;

    private DataSpec mDataSpec;

    private final ConcurrentLinkedQueue<HtspMessage> mQueue = new ConcurrentLinkedQueue<>();
    private byte[] mBuffer;
    private int mBufferRemaining;

    private boolean mIsOpen = false;

    public HtspDataSource(Context context, SimpleHtspConnection connection, String streamProfile) {
        Log.d(TAG, "New HtspDataSource instantiated");

        mContext = context;
        mConnection = connection;
        mStreamProfile = streamProfile;

        mBufferRemaining = 0;

        mSubscriber = new Subscriber(mConnection, this);
        mConnection.addAuthenticationListener(mSubscriber);
    }

    // DataSource Methods
    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Log.i(TAG, "Opening HTSP DataSource");
        mDataSpec = dataSpec;

        try {
            mSubscriber.subscribe(Long.parseLong(dataSpec.uri.getHost()), mStreamProfile);
        } catch (HtspNotConnectedException e) {
            throw new IOException("Failed to open DataSource, HTSP not connected", e);
        }

        mIsOpen = true;

        return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }

        if (mBufferRemaining == 0) {
            // We need to consume another message to process this
            HtspMessage message = null;
            try {
                while (mIsOpen && message == null) {
                    message = mQueue.poll();
                    if (message == null) {
                        if (Constants.DEBUG)
                            Log.v(TAG, "Blocking for more data");
                        Thread.sleep(250);
                    }
                }
            } catch (InterruptedException e) {
                // Ignore.
                Log.w(TAG, "Caught InterruptedException, ignoring");
            }

            // Serialize the message to the output buffer
            serializeMessageToBuffer(message);
        }

        if (!mIsOpen && mBufferRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int bufferOffset = mBuffer.length - mBufferRemaining;
        int bytesToRead = Math.min(mBufferRemaining, readLength);

        System.arraycopy(mBuffer, bufferOffset, buffer, offset, bytesToRead);
        mBufferRemaining -= bytesToRead;

        return bytesToRead;
    }

    @Override
    public Uri getUri() {
        if (mDataSpec != null) {
            return mDataSpec.uri;
        }

        return null;
    }

    @Override
    public void close() throws IOException {
        Log.i(TAG, "Closing HTSP DataSource");
        mIsOpen = false;

        mConnection.removeAuthenticationListener(mSubscriber);
        mSubscriber.unsubscribe();

        // Watch for memory leaks
        Application.getRefWatcher(mContext).watch(this);
    }

    // Subscription.Listener Methods
    @Override
    public void onSubscriptionStart(@NonNull HtspMessage message) {
        Log.d(TAG, "Received subscriptionStart");
        mQueue.add(message);
    }

    @Override
    public void onSubscriptionStatus(@NonNull HtspMessage message) {

    }

    @Override
    public void onSubscriptionStop(@NonNull HtspMessage message) {
        Log.d(TAG, "Received subscriptionStop");
        mIsOpen = false;
    }

    @Override
    public void onQueueStatus(@NonNull HtspMessage message) {

    }

    @Override
    public void onSignalStatus(@NonNull HtspMessage message) {

    }

    @Override
    public void onMuxpkt(@NonNull HtspMessage message) {
        mQueue.add(message);
    }

    // Misc Internal Methods
    private void serializeMessageToBuffer(@NonNull HtspMessage message) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (
                ObjectOutputStream objectOutput = new ObjectOutputStream(outputStream);
        ) {
            objectOutput.writeUnshared(message);
            objectOutput.flush();

            mBuffer = outputStream.toByteArray();
            mBufferRemaining = mBuffer.length;
        } catch (IOException e) {
            // Ignore?
            Log.w(TAG, "Caught IOException, ignoring", e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }
}
