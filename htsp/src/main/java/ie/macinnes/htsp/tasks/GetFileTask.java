/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package ie.macinnes.htsp.tasks;

import android.content.Context;
import android.media.tv.TvContract;
import android.os.Handler;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.htsp.MessageListener;
import ie.macinnes.htsp.ResponseMessage;
import ie.macinnes.htsp.messages.FileCloseRequest;
import ie.macinnes.htsp.messages.FileOpenRequest;
import ie.macinnes.htsp.messages.FileOpenResponse;
import ie.macinnes.htsp.messages.FileReadRequest;
import ie.macinnes.htsp.messages.FileReadResponse;

public class GetFileTask extends MessageListener {
    private static final String TAG = GetFileTask.class.getName();

    protected Context mContext;
    protected AtomicInteger mOpenCount;

    // Map of <Seq, OpenID>'s
    protected Map<Long, Integer> mSequences;

    // Map of <OpenID, ID>'s
    protected Map<Integer, Integer> mIds;

    // Map of <OpenID, IFileGetCallback>'s
    protected Map<Integer, IFileGetCallback> mCallbacks;

    // Map of <OpenID, ByteBuffer>'s
    protected Map<Integer, ByteBuffer> mBuffers;

    public GetFileTask(Context context, Handler handler) {
        super(handler);

        mContext = context;

        mOpenCount = new AtomicInteger(1);
        mSequences = new HashMap<>();
        mIds = new HashMap<>();
        mCallbacks = new HashMap<>();
        mBuffers = new HashMap<>();
    }

    public void getFile(String file, IFileGetCallback callback) {
        // Generate a new FileOpen ID
        int openCount = mOpenCount.getAndIncrement();

        // Store the callback for later use
        mCallbacks.put(openCount, callback);

        sendFileOpen(openCount, file);
    }

    @Override
    public void onMessage(ResponseMessage message) {
        long sequence = message.getSeq();

        if (!mSequences.containsKey(sequence)) {
            return;
        }

        Log.v(TAG, "Received Message: " + message.getClass() + " / " + message.toString());

        int openCount = mSequences.get(sequence);
        mSequences.remove(message.getSeq());

        if (message.getError() != null) {
            Log.e(TAG, "Error handling a GetFileTask message");
            mCallbacks.get(openCount).onFailure();
            sendFileClose(openCount);
            cleanup(openCount);
            return;
        }

        if (message.getClass() == FileOpenResponse.class) {
            FileOpenResponse fileOpenResponse = (FileOpenResponse) message;
            createByteBuffer(openCount, (FileOpenResponse) message);
            sendFileRead(openCount, fileOpenResponse);
        } else if (message.getClass() == FileReadResponse.class) {
            FileReadResponse fileReadResponse = (FileReadResponse) message;
            ByteBuffer buffer = mBuffers.get(openCount);
            buffer.put(fileReadResponse.getData());
            if (buffer.position() == buffer.capacity()) {
                // We've read the entire file
                sendFileClose(openCount);
                triggerCallback(openCount);
            } else {
                // There's more data to read..
                sendFileRead(openCount, fileReadResponse);
            }
        }
    }

    private void sendFileOpen(int openCount, String file) {
        Log.d(TAG, "Prepping fileOpenRequest");
        FileOpenRequest fileOpenRequest = new FileOpenRequest();

        fileOpenRequest.setFile(file);

        Log.d(TAG, "Sending fileOpenRequest");
        mSequences.put(fileOpenRequest.getSeq(), openCount);
        mConnection.sendMessage(fileOpenRequest);
    }

    private void createByteBuffer(int openCount, FileOpenResponse response) {
        Long size = response.getSize();
        mBuffers.put(openCount, ByteBuffer.allocate(size.intValue()));
    }

    private void sendFileRead(int openCount, FileOpenResponse response) {
        mIds.put(openCount, response.getId());
        sendFileRead(openCount);
    }

    private void sendFileRead(int openCount, FileReadResponse response) {
        sendFileRead(openCount);
    }

    private void sendFileRead(int openCount) {
        Log.d(TAG, "Prepping fileReadRequest");
        FileReadRequest fileReadRequest = new FileReadRequest();

        fileReadRequest.setId(mIds.get(openCount));
        fileReadRequest.setSize(10240);
        fileReadRequest.setOffset(mBuffers.get(openCount).position());

        Log.d(TAG, "Sending fileReadRequest");
        mSequences.put(fileReadRequest.getSeq(), openCount);
        mConnection.sendMessage(fileReadRequest);
    }

    private void sendFileClose(int openCount) {
        if (mIds.containsKey(openCount)) {
            Log.d(TAG, "Prepping fileCloseRequest");
            FileCloseRequest fileCloseRequest = new FileCloseRequest();

            fileCloseRequest.setId(mIds.get(openCount));

            Log.d(TAG, "Sending fileCloseRequest");
            mSequences.put(fileCloseRequest.getSeq(), openCount);
            mConnection.sendMessage(fileCloseRequest);
        }
    }

    private void triggerCallback(int openCount) {
        IFileGetCallback callback = mCallbacks.get(openCount);

        ByteBuffer buffer = mBuffers.get(openCount);
        buffer.flip();
        callback.onSuccess(buffer);

        cleanup(openCount);
    }

    private void cleanup(int openCount) {
        // Cleanup
        mCallbacks.remove(openCount);
        mIds.remove(openCount);
        mBuffers.remove(openCount);
    }

    public interface IFileGetCallback {
        void onSuccess(ByteBuffer buffer);
        void onFailure();
    }
}
