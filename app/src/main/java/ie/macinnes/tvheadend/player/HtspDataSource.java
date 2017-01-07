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
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    protected BlockingQueue<ResponseMessage> mMessageQueue;
    protected ByteBuffer mBuffer;

    public HtspDataSource(Context context, Connection connection, int subscriptionId) {
        mContext = context;
        mConnection = connection;
        mSubscriptionId = subscriptionId;

        mMessageQueue = new ArrayBlockingQueue<>(500);
        mBuffer = ByteBuffer.allocate(1048576); // 1 MB

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
    synchronized public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }

        // If the buffer doesn't have enough data for this read, add another message to it
        if (mBuffer.position() < readLength) {
            // Grab a message off the queue, serialize it to the buffer.
            ResponseMessage message = mMessageQueue.poll();

            if (message != null) {
                serializeMessageToBuffer(message);
            }
        }

        int remaining = mBuffer.remaining();
        int length = remaining >= readLength ? readLength : remaining;

        mBuffer.get(buffer, offset, length);
        mBuffer.compact();

        return length;
    }

    private void serializeMessageToBuffer(ResponseMessage message) {
        // TODO.. Ensure we don't overflow the buffer.

        if (message instanceof MuxpktResponse) {
            serializeMessageToBuffer((MuxpktResponse) message);
        } else if (message instanceof SubscriptionStartResponse) {
            serializeMessageToBuffer((SubscriptionStartResponse) message);
        } else if (message instanceof SubscriptionStatusResponse) {
            serializeMessageToBuffer((SubscriptionStatusResponse) message);
        }
    }

    private void serializeMessageToBuffer(MuxpktResponse message) {
        mBuffer.putShort(MSG_TYPE_MUXPKT);
//        mBuffer.putInt(message.getSubscriptionId());
//        mBuffer.putInt(message.getFrameType());
        mBuffer.putInt(message.getStream());
//        mBuffer.putLong(message.getDts());
//        mBuffer.putLong(message.getPts());
//        mBuffer.putInt(message.getDuration());
        mBuffer.putInt(message.getPayloadLength());
        mBuffer.put(message.getPayload());
    }

    private void serializeMessageToBuffer(SubscriptionStartResponse message) {
        mBuffer.putShort(MSG_TYPE_SUBSCRIPTION_START);
        mBuffer.putInt(message.getSubscriptionId());
    }

    private void serializeMessageToBuffer(SubscriptionStatusResponse message) {
        mBuffer.putShort(MSG_TYPE_SUBSCRIPTION_STATUS);
        mBuffer.putInt(message.getSubscriptionId());
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
                if (message instanceof MuxpktResponse) {
                    mMessageQueue.offer(message, 5, TimeUnit.SECONDS);
                } else if (message instanceof SubscriptionStartResponse ||
                        message instanceof SubscriptionStatusResponse) {
                    mMessageQueue.put(message);
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while awaiting a queue slot for a message");
            }
        }
    }
}
