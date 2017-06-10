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

import ie.macinnes.htsp.HtspFileInputStream;
import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.HtspNotConnectedException;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;
import ie.macinnes.tvheadend.Application;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;

public class HtspRecordingDataSource extends HtspDataSource {
    private static final String TAG = HtspRecordingDataSource.class.getName();
    private static final int BUFFER_SIZE = 10*1024*1024;
    private static final AtomicInteger sDataSourceCount = new AtomicInteger();

    public static class Factory extends HtspDataSource.Factory {
        private static final String TAG = Factory.class.getName();

        private final Context mContext;
        private final SimpleHtspConnection mConnection;

        public Factory(Context context, SimpleHtspConnection connection) {
            mContext = context;
            mConnection = connection;
        }

        @Override
        public HtspDataSource createDataSourceInternal() {
            return new HtspRecordingDataSource(mContext, mConnection);
        }

    }

    private final int mDataSourceNumber;
    private HtspFileInputStream mHtspFileInputStream;

    public HtspRecordingDataSource(Context context, SimpleHtspConnection connection) {
        super(context, connection);

        mDataSourceNumber = sDataSourceCount.incrementAndGet();

        Log.d(TAG, "New HtspChannelDataSource instantiated ("+mDataSourceNumber+")");
    }

    @Override
    protected void finalize() throws Throwable {
        // This is a total hack, but there's not much else we can do?
        // https://github.com/google/ExoPlayer/issues/2662 - Luckily, i've not found it's actually
        // been used anywhere at this moment.
        if (mConnection != null) {
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
        Log.i(TAG, "Opening HTSP DataSource ("+mDataSourceNumber+")");

        mDataSpec = dataSpec;

        String fileName = "dvrfile" + dataSpec.uri.getPath();

        mHtspFileInputStream = new HtspFileInputStream(mConnection, fileName);

        return mHtspFileInputStream.getFileSize();
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return mHtspFileInputStream.read(buffer, offset, readLength);
    }

    @Override
    public void close() throws IOException {
        Log.i(TAG, "Closing HTSP DataSource ("+mDataSourceNumber+")");
        if (mHtspFileInputStream != null) {
            mHtspFileInputStream.close();
        }
    }

    // HtspDataSource Methods
    public void release() {
        if (mConnection != null) {
            mConnection = null;
        }

        if (mHtspFileInputStream != null) {
            try {
                mHtspFileInputStream.close();
            } catch (IOException e) {
                // Ignore.
            }
            mHtspFileInputStream = null;
        }

        // Watch for memory leaks
        Application.getRefWatcher(mContext).watch(this);
    }

    @Override
    public void pause() {
        // No action needed
    }

    @Override
    public void resume() {
        // No action needed
    }

//    @Override
//    public void seek(long timeMs) {
//        // No action needed
//    }

    @Override
    public long getTimeshiftStartTime() {
        // No action needed?
        return INVALID_TIMESHIFT_TIME;
    }

    @Override
    public long getTimeshiftStartPts() {
        // No action needed?
        return INVALID_TIMESHIFT_TIME;
    }

    @Override
    public long getTimeshiftOffsetPts() {
        // No action needed?
        return INVALID_TIMESHIFT_TIME;
    }

    @Override
    public void setSpeed(int speed) {
        // No action needed
    }
}
