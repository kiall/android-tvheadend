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

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.Closeable;
import java.lang.ref.WeakReference;

import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Subscriber;

public abstract class HtspDataSource implements DataSource, Closeable {
    public static final long INVALID_TIMESHIFT_TIME = Subscriber.INVALID_TIMESHIFT_TIME;

    public static abstract class Factory implements DataSource.Factory {

        private static final String TAG = HtspDataSource.Factory.class.getName();

        private WeakReference<HtspDataSource> mCurrentDataSource;

        @Override
        public HtspDataSource createDataSource() {
            releaseCurrentDataSource();

            mCurrentDataSource = new WeakReference<>(createDataSourceInternal());
            return mCurrentDataSource.get();
        }

        public HtspDataSource getCurrentDataSource() {
            if (mCurrentDataSource != null) {
                return mCurrentDataSource.get();
            }

            return null;
        }

        public void releaseCurrentDataSource() {
            if (mCurrentDataSource != null) {
                mCurrentDataSource.get().release();
                mCurrentDataSource.clear();
                mCurrentDataSource = null;
            }
        }

        protected abstract HtspDataSource createDataSourceInternal();
    }

    final Context mContext;
    SimpleHtspConnection mConnection;
    DataSpec mDataSpec;

    HtspDataSource(Context context, SimpleHtspConnection connection) {
        mContext = context;
        mConnection = connection;
    }

    protected abstract void release();

    // Methods used by the player, which need to be passed to the Subscriber
    public abstract void pause();
    public abstract void resume();
    public abstract long getTimeshiftStartTime();
    public abstract long getTimeshiftStartPts();
    public abstract long getTimeshiftOffsetPts();
    public abstract void setSpeed(int speed);

    // DataSource Methods
    @Override
    public Uri getUri() {
        if (mDataSpec != null) {
            return mDataSpec.uri;
        }

        return null;
    }
}
