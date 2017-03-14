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
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Application;
import ie.macinnes.tvheadend.Constants;

public class HtspDataSource implements DataSource, Subscriber.Listener, Closeable {
    private static final String TAG = HtspDataSource.class.getName();
    private static final int BUFFER_SIZE = 10*1024*1024;

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

    private ByteBuffer mBuffer;
    private ReentrantLock mLock = new ReentrantLock();

    private boolean mIsOpen = false;

    public HtspDataSource(Context context, SimpleHtspConnection connection, String streamProfile) {
        Log.d(TAG, "New HtspDataSource instantiated");

        mContext = context;
        mConnection = connection;
        mStreamProfile = streamProfile;

        mBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        mBuffer.limit(0);

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

        // If the buffer is empty, block until we have at least 1 byte
        while (mIsOpen && mBuffer.remaining() == 0) {
            try {
                if (Constants.DEBUG)
                    Log.v(TAG, "Blocking for more data");
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // Ignore.
                Log.w(TAG, "Caught InterruptedException, ignoring");
            }
        }

        if (!mIsOpen && mBuffer.remaining() == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int length;

        mLock.lock();
        try {
            int remaining = mBuffer.remaining();
            length = remaining >= readLength ? readLength : remaining;

            mBuffer.get(buffer, offset, length);
            mBuffer.compact();
            mBuffer.flip();
        } finally {
            mLock.unlock();
        }

        return length;
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
        serializeMessageToBuffer(message);
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
        serializeMessageToBuffer(message);
    }

    // Misc Internal Methods
    private void serializeMessageToBuffer(@NonNull HtspMessage message) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        mLock.lock();
        try (
                ObjectOutputStream objectOutput = new ObjectOutputStream(outputStream);
        ) {
            objectOutput.writeUnshared(message);
            objectOutput.flush();

            mBuffer.position(mBuffer.limit());
            mBuffer.limit(mBuffer.capacity());

            mBuffer.put(outputStream.toByteArray());

            mBuffer.flip();
        } catch (IOException e) {
            // Ignore?
            Log.w(TAG, "Caught IOException, ignoring", e);
        } finally {
            mLock.unlock();
            try {
                outputStream.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }
}
