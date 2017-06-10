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
import com.google.android.exoplayer2.upstream.DataSpec;

import org.acra.ACRA;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Application;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;

public class HtspChannelDataSource extends HtspDataSource implements Subscriber.Listener {
    private static final String TAG = HtspChannelDataSource.class.getName();
    private static final AtomicInteger sDataSourceCount = new AtomicInteger();
    private static final int BUFFER_SIZE = 10*1024*1024;
    public static final byte[] HEADER = new byte[] {0,1,0,1,0,1,0,1};

    public static class Factory extends HtspDataSource.Factory {
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
        public HtspDataSource createDataSourceInternal() {
            return new HtspChannelDataSource(mContext, mConnection, mStreamProfile);
        }

    }

    private String mStreamProfile;

    private final SharedPreferences mSharedPreferences;
    private int mTimeshiftPeriod = 0;

    private final int mDataSourceNumber;
    private Subscriber mSubscriber;

    private ByteBuffer mBuffer;
    private ReentrantLock mLock = new ReentrantLock();

    private boolean mIsOpen = false;
    private boolean mIsSubscribed = false;

    public HtspChannelDataSource(Context context, SimpleHtspConnection connection, String streamProfile) {
        super(context, connection);

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

        mDataSourceNumber = sDataSourceCount.incrementAndGet();

        Log.d(TAG, "New HtspChannelDataSource instantiated ("+mDataSourceNumber+")");

        try {
            // Create the buffer, and place the HTSPChannelDataSource header in place.
            mBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            mBuffer.limit(HEADER.length);
            mBuffer.put(HEADER);
            mBuffer.position(0);
        } catch (OutOfMemoryError e) {
            // Since we're allocating a large buffer here, it's fairly safe to assume we'll have
            // enough memory to catch and throw this exception. We do this, as each OOM exception
            // message is unique (lots of #'s of bytes available/used/etc) and means crash reporting
            // doesn't group things nicely.
            throw new RuntimeException("OutOfMemoryError when allocating HtspChannelDataSource buffer ("+mDataSourceNumber+")", e);
        }

        mSubscriber = new Subscriber(mConnection);
        mSubscriber.addSubscriptionListener(this);
        mConnection.addAuthenticationListener(mSubscriber);
    }

    @Override
    protected void finalize() throws Throwable {
        // This is a total hack, but there's not much else we can do?
        // https://github.com/google/ExoPlayer/issues/2662 - Luckily, i've not found it's actually
        // been used anywhere at this moment.
        if (mSubscriber != null || mConnection != null) {
            Log.e(TAG, "Datasource finalize relied upon to release the subscription");

            release();

            try {
                // If we see this in the wild, I want to know about it. Fake an exception and send
                // and crash report.
                ACRA.getErrorReporter().handleException(new Exception(
                        "Datasource finalize relied upon to release the subscription"));
            } catch (IllegalStateException e) {
                // Ignore, ACRA is not available.
            }
        }
    }

    // DataSource Methods
    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Log.i(TAG, "Opening HtspChannelDataSource ("+mDataSourceNumber+")");
        mDataSpec = dataSpec;

        if (!mIsSubscribed) {
            try {
                long channelId = Long.parseLong(dataSpec.uri.getPath().substring(1));
                mSubscriber.subscribe(channelId, mStreamProfile, mTimeshiftPeriod);
                mIsSubscribed = true;
            } catch (HtspNotConnectedException e) {
                throw new IOException("Failed to open HtspChannelDataSource, HTSP not connected (" + mDataSourceNumber + ")", e);
            }
        }

        long seekPosition = mDataSpec.position;
        if (seekPosition > 0 && mTimeshiftPeriod > 0) {
            Log.d(TAG, "seek to time PTS: " + seekPosition);

            mSubscriber.skip(seekPosition);
            mBuffer.clear();
            mBuffer.limit(0);
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
                    Log.v(TAG, "Blocking for more data ("+mDataSourceNumber+")");
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // Ignore.
                Log.w(TAG, "Caught InterruptedException ("+mDataSourceNumber+")");
                return 0;
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
    public void close() throws IOException {
        Log.i(TAG, "Closing HTSP DataSource ("+mDataSourceNumber+")");
        mIsOpen = false;
    }

    // Subscription.Listener Methods
    @Override
    public void onSubscriptionStart(@NonNull HtspMessage message) {
        Log.d(TAG, "Received subscriptionStart ("+mDataSourceNumber+")");
        serializeMessageToBuffer(message);
    }

    @Override
    public void onSubscriptionStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSubscriptionSkip(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSubscriptionSpeed(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSubscriptionStop(@NonNull HtspMessage message) {
        Log.d(TAG, "Received subscriptionStop ("+mDataSourceNumber+")");
        mIsOpen = false;
    }

    @Override
    public void onQueueStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onSignalStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onTimeshiftStatus(@NonNull HtspMessage message) {
        // Don't care about this event here
    }

    @Override
    public void onMuxpkt(@NonNull HtspMessage message) {
        serializeMessageToBuffer(message);
    }

    // HtspDataSource Methods
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
    public void pause() {
        if (mSubscriber != null) {
            mSubscriber.pause();
        }
    }

    @Override
    public void resume() {
        if (mSubscriber != null) {
            mSubscriber.resume();
        }
    }

//    @Override
//    public void seek(long timeMs) {
//        // TODO?
//    }

    @Override
    public long getTimeshiftStartTime() {
        if (mSubscriber != null) {
            return mSubscriber.getTimeshiftStartTime();
        }

        return INVALID_TIMESHIFT_TIME;
    }

    @Override
    public long getTimeshiftStartPts() {
        if (mSubscriber != null) {
            return mSubscriber.getTimeshiftStartPts();
        }

        return INVALID_TIMESHIFT_TIME;
    }

    @Override
    public long getTimeshiftOffsetPts() {
        if (mSubscriber != null) {
            return mSubscriber.getTimeshiftOffsetPts();
        }

        return INVALID_TIMESHIFT_TIME;
    }

    @Override
    public void setSpeed(int speed) {

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
            Log.w(TAG, "Caught IOException, ignoring ("+mDataSourceNumber+")", e);
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
