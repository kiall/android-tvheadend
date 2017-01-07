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
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import ie.macinnes.htsp.BaseMessage;
import ie.macinnes.htsp.Connection;
import ie.macinnes.htsp.MessageListener;
import ie.macinnes.htsp.ResponseMessage;
import ie.macinnes.htsp.messages.MuxpktResponse;
import ie.macinnes.htsp.messages.SubscribeRequest;
import ie.macinnes.htsp.messages.SubscriptionStartResponse;
import ie.macinnes.htsp.messages.SubscriptionStatusResponse;
import ie.macinnes.htsp.messages.UnsubscribeRequest;


public class HtspDataSource implements DataSource {
    private static final String TAG = HtspDataSource.class.getName();

    public static class Factory implements DataSource.Factory {
        private static final String TAG = Factory.class.getName();

        protected Context mContext;
        protected Connection mConnection;
        protected AtomicInteger mDataSourceCount = new AtomicInteger();

        public Factory(Context context, Connection connection) {
            mContext = context;
            mConnection = connection;
        }

        @Override
        public HtspDataSource createDataSource() {
            return new HtspDataSource(mContext, mConnection, mDataSourceCount.getAndIncrement());
        }
    }

    public static final short MSG_TYPE_MUXPKT = 1;
    public static final short MSG_TYPE_SUBSCRIPTION_START = 2;
    public static final short MSG_TYPE_SUBSCRIPTION_STATUS = 3;

    protected Context mContext;
    protected Connection mConnection;
    protected int mSubscriptionId;
    protected SubscriptionTask mSubscriptionTask;

    protected DataSpec mDataSpec;

    protected ByteBuffer mBuffer;
    protected ReentrantLock mLock = new ReentrantLock();

    public HtspDataSource(Context context, Connection connection, int subscriptionId) {
        mContext = context;
        mConnection = connection;
        mSubscriptionId = subscriptionId;

        mBuffer = ByteBuffer.allocate(30000000); // 30 MB
        mBuffer.limit(0);

        mSubscriptionTask = new SubscriptionTask();
        mConnection.addMessageListener(mSubscriptionTask);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        mDataSpec = dataSpec;

        SubscribeRequest subscribeRequest = new SubscribeRequest();

        subscribeRequest.setSubscriptionId((long) mSubscriptionId);
        subscribeRequest.setChannelId(Long.parseLong(dataSpec.uri.getHost()));

        mConnection.sendMessage(subscribeRequest);

        return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }

        // If the buffer is empty, block until we have one.
        while (mBuffer.remaining() == 0 && !Thread.interrupted()) {
            try {
                Log.d(TAG, "Blocking for more data");
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        int length;

        mLock.lock();
        try {
            int remaining = mBuffer.remaining();
            length = remaining >= readLength ? readLength : remaining;

            mBuffer.get(buffer, offset, length);
            mBuffer.compact();
        } finally {
            mLock.unlock();
        }

        return length;
    }

    private void serializeMessageToBuffer(ResponseMessage message) {
        // TODO.. Ensure we don't overflow the buffer.

        // Block until we have at least 3MB free in the buffer.. Hack...
        while (mBuffer.capacity() - mBuffer.remaining() < 3000000  && !Thread.interrupted()) {
            try {
                Log.d(TAG, "Blocking for more space");
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        mLock.lock();
        try {
            mBuffer.position(mBuffer.limit());
            mBuffer.limit(mBuffer.capacity());

            if (message instanceof MuxpktResponse) {
//                serializeMessageToBuffer((MuxpktResponse) message);
            } else if (message instanceof SubscriptionStartResponse) {
                serializeMessageToBuffer((SubscriptionStartResponse) message);
            } else if (message instanceof SubscriptionStatusResponse) {
                serializeMessageToBuffer((SubscriptionStatusResponse) message);
            }

            mBuffer.flip();
        } finally {
            mLock.unlock();
        }
    }

    private void serializeMessageToBuffer(MuxpktResponse message) {
        Log.w(TAG, "HtspDataSource - MSG_TYPE_MUXPKT");
        mBuffer.putShort(MSG_TYPE_MUXPKT);
//        mBuffer.putInt(message.getSubscriptionId());
//        mBuffer.putInt(message.getFrameType());
//        mBuffer.putInt(message.getStream());
//        mBuffer.putLong(message.getDts());
//        mBuffer.putLong(message.getPts());
//        mBuffer.putInt(message.getDuration());
//        mBuffer.putInt(message.getPayloadLength());
//        mBuffer.put(message.getPayload());
    }

    private void serializeMessageToBuffer(SubscriptionStartResponse message) {
        Log.w(TAG, "HtspDataSource - MSG_TYPE_SUBSCRIPTION_START");
        mBuffer.putShort(MSG_TYPE_SUBSCRIPTION_START);
//        mBuffer.putInt(message.getSubscriptionId());
    }

    private void serializeMessageToBuffer(SubscriptionStatusResponse message) {
        Log.w(TAG, "HtspDataSource - MSG_TYPE_SUBSCRIPTION_STATUS");
        mBuffer.putShort(MSG_TYPE_SUBSCRIPTION_STATUS);
//        mBuffer.putInt(message.getSubscriptionId());
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
        UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest();
        unsubscribeRequest.setSubscriptionId((long) mSubscriptionId);

        mConnection.sendMessage(unsubscribeRequest);
        mConnection.removeMessageListener(mSubscriptionTask);
    }

    private class SubscriptionTask extends MessageListener {
        @Override
        public void onMessage(ResponseMessage message) {
            try {
                serializeMessageToBuffer(message);
            } catch (BufferOverflowException e) {
                Log.v(TAG, "BufferOverflowException...", e);
            }
        }
    }
}
