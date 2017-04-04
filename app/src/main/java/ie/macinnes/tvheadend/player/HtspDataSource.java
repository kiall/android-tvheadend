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
import android.content.SharedPreferences;
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
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Application;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;

public class HtspDataSource implements DataSource, Subscriber.Listener, Closeable {
    private static final String TAG = HtspDataSource.class.getName();
    private static final boolean DEBUG = Constants.DEBUG;
    private static final int BUFFER_SIZE = 10*1024*1024;

    public static class Factory implements DataSource.Factory {
        private static final String TAG = Factory.class.getName();

        private final Context mContext;
        private final SimpleHtspConnection mConnection;
        private final String mStreamProfile;

        private WeakReference<HtspDataSource> mMostRecentDataSource;

        public Factory(Context context, SimpleHtspConnection connection, String streamProfile) {
            mContext = context;
            mConnection = connection;
            mStreamProfile = streamProfile;
        }

        @Override
        public HtspDataSource createDataSource() {
            HtspDataSource oldDataSource = getMostRecentDataSource();
            if (oldDataSource != null) {
                oldDataSource.release();
            }
            mMostRecentDataSource = new WeakReference<>(new HtspDataSource(mContext, mConnection, mStreamProfile));
            return mMostRecentDataSource.get();
        }

        public HtspDataSource getMostRecentDataSource() {
            if (mMostRecentDataSource != null) {
                return mMostRecentDataSource.get();
            }

            return null;
        }
    }

    private Context mContext;
    private SimpleHtspConnection mConnection;
    private String mStreamProfile;

    private final SharedPreferences mSharedPreferences;
    private int mTimeshiftPeriod = 0;

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

        mSharedPreferences = mContext.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        boolean timeshiftEnabled = mSharedPreferences.getBoolean(
                Constants.KEY_TIMESHIFT_ENABLED,
                mContext.getResources().getBoolean(R.bool.pref_default_timeshift_enabled));

        if (timeshiftEnabled) {
            // TODO: Eventually, this should be a preference.
            mTimeshiftPeriod = 3600;
        }

        try {
            mBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            mBuffer.limit(0);
        } catch (OutOfMemoryError e) {
            // Since we're allocating a large buffer here, it's fairly safe to assume we'll have
            // enough memory to catch and throw this exception. We do this, as each OOM exception
            // message is unique (lots of #'s of bytes available/used/etc) and means crash reporting
            // doesn't group things nicely.
            throw new RuntimeException("OutOfMemoryError when allocating HtspDataSource buffer", e);
        }

        mSubscriber = new Subscriber(mConnection);
        mSubscriber.addSubscriptionListener(this);
        mConnection.addAuthenticationListener(mSubscriber);
    }

    public Subscriber getSubscriber() {
        return mSubscriber;
    }

    public void release() {
        if (mConnection != null) {
            mConnection.removeAuthenticationListener(mSubscriber);
            mConnection = null;
        }

        if (mSubscriber != null) {
            mSubscriber.removeSubscriptionListener(this);
            mSubscriber.unsubscribe();
            mSubscriber = null;
        }

        // Watch for memory leaks
        Application.getRefWatcher(mContext).watch(this);
    }

    @Override
    protected void finalize() throws Throwable {
        // This is a total hack, but there's not much else we can do?
        // https://github.com/google/ExoPlayer/issues/2662
        if (mSubscriber != null || mConnection != null) {
            Log.w(TAG, "Datasource finalize relied upon to release the subscription");
            release();
        }
    }

    // DataSource Methods
    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Log.i(TAG, "Opening HTSP DataSource");
        mDataSpec = dataSpec;

        mIsOpen = true;

        long seekPosition = mDataSpec.position;
        if (seekPosition > 0) {
            Log.d(TAG, "seek to time PTS: " + seekPosition);
            mSubscriber.skip(seekPosition);
            mBuffer.clear();
            mBuffer.limit(0);
            return C.LENGTH_UNSET;
        }

        try {
            mSubscriber.subscribe(Long.parseLong(dataSpec.uri.getHost()), mStreamProfile, mTimeshiftPeriod);
        } catch (HtspNotConnectedException e) {
            mIsOpen = false;
            throw new IOException("Failed to open DataSource, HTSP not connected", e);
        }

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
                if (DEBUG)
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
    public void onSubscriptionSkip(@NonNull HtspMessage message) {

    }

    @Override
    public void onSubscriptionSpeed(@NonNull HtspMessage message) {

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
    public void onTimeshiftStatus(@NonNull HtspMessage message) {

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
