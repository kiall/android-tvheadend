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
package ie.macinnes.htsp;

import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class Connection implements Runnable {
    private static final String TAG = Connection.class.getName();

    public final static int STATE_CLOSED = 0;
    public final static int STATE_CONNECTING = 1;
    public final static int STATE_CONNECTED = 2;
    public final static int STATE_CLOSING = 3;
    public final static int STATE_FAILED = 4;

    protected SocketChannel mSocketChannel;
    protected Selector mSelector;

    protected ByteBuffer mReadBuffer;

    protected Queue<HtspMessage> mMessageQueue;

    protected boolean mRunning = false;
    protected int mState = STATE_CLOSED;

    protected String mHostname;
    protected int mPort;
    protected int mBufferSize;

    protected List<IConnectionListener> mHTSPConnectionListeners = new ArrayList<>();
    protected List<IMessageListener> mMessageListeners = new ArrayList<>();

    public Connection(String hostname, int port) {
        // 1048576 = 1 MB
        this(hostname, port, 1048576);
    }

    public Connection(String hostname, int port, int bufferSize) {
        mHostname = hostname;
        mPort = port;
        mBufferSize = bufferSize;
        mMessageQueue = new LinkedList<HtspMessage>();
    }

    public void addConnectionListener(IConnectionListener listener) {
        if (mHTSPConnectionListeners.contains(listener)) {
            Log.w(TAG, "Attempted to add duplicate connection listener");
            return;
        }
        mHTSPConnectionListeners.add(listener);
    }

    public void addMessageListener(IMessageListener listener) {
        if (mMessageListeners.contains(listener)) {
            Log.w(TAG, "Attempted to add duplicate message listener");
            return;
        }
        listener.setConnection(this);
        mMessageListeners.add(listener);
    }

    @Override
    public void run() {
        try {
            open();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open HTSP connection", e);
            setState(STATE_FAILED);
            return;
        }

        while (mRunning) {
            try {
                mSelector.select();
            } catch (IOException e) {
                Log.e(TAG, "Failed to select from socket channel", e);
                mRunning = false;
                setState(STATE_FAILED);
                break;
            }

            if (!mSelector.isOpen()) {
                break;
            }

            Set<SelectionKey> keys = mSelector.selectedKeys();
            Iterator<SelectionKey> i = keys.iterator();

            try {
                while (i.hasNext()) {
                    SelectionKey selectionKey = i.next();
                    i.remove();

                    if (!selectionKey.isValid()) {
                        break;
                    }

                    if (selectionKey.isValid() && selectionKey.isConnectable()) {
                        processConnectableSelectionKey();
                    }

                    if (selectionKey.isValid() && selectionKey.isReadable()) {
                        processReadableSelectionKey();
                    }

                    if (selectionKey.isValid() && selectionKey.isWritable()) {
                        processWritableSelectionKey();
                    }
                }

                if (mSocketChannel.isConnected() && mMessageQueue.isEmpty()) {
                    mSocketChannel.register(mSelector, SelectionKey.OP_READ);
                } else if (mSocketChannel.isConnected()) {
                    mSocketChannel.register(mSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Something failed - shutting down", e);
                mRunning = false;
                setState(STATE_FAILED);
            }
        }

        if (getState() != STATE_CLOSED) {
            close();
        }
    }

    public void open() throws IOException {
        Log.i(TAG, "Opening HTSP Connection");

        if (mSocketChannel != null) {
            throw new RuntimeException("Attempted to open HTSP connection twice");
        }

        setState(STATE_CONNECTING);

        mReadBuffer = ByteBuffer.allocate(mBufferSize);

        final Object openLock = new Object();

        mSocketChannel = SocketChannel.open();

        try {
            mSocketChannel.connect(new InetSocketAddress(mHostname, mPort));
        } catch (ConnectException e) {
            Log.w(TAG, "HTSP Connection Failure", e);
            setState(STATE_FAILED);
            return;
        }

        mSocketChannel.configureBlocking(false);

        try {
            mSelector = Selector.open();
        } catch (ConnectException e) {
            Log.w(TAG, "HTSP Connection Failure to open Selector", e);
            setState(STATE_FAILED);
            return;
        }

        mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT, openLock);

        mRunning = true;

        synchronized (openLock) {
            try {
                openLock.wait(2000);
                if (mSocketChannel.isConnectionPending()) {
                    Log.w(TAG, "Timeout while registering selector");
                    close();
                    return;
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while registering selector", e);
                close();
                return;
            }
        }

        Log.i(TAG, "HTSP Connected");
        setState(STATE_CONNECTED);
    }

    public void close() {
        close(STATE_CLOSING);
    }

    public void close(int startState) {
        if (getState() == STATE_CLOSED) {
            Log.d(TAG, "Connection already closed, ignoring close request");
            return;
        }

        Log.i(TAG, "Closing HTSP Connection");

        mRunning = false;
        setState(startState);

        // Clear out any pending messages
        mMessageQueue.clear();
        mReadBuffer.clear();

        if (mSocketChannel != null) {
            try {
                Log.w(TAG, "Calling SocketChannel close");
                mSocketChannel.socket().close();
                mSocketChannel.close();
                mSocketChannel = null;
                if (mSelector != null) {
                    mSelector.close();
                }
                mSelector = null;
            } catch (IOException e) {
                Log.w(TAG, "Failed to close socket channel: " + e.getLocalizedMessage());
            }
        }

        mSocketChannel = null;
        setState(STATE_CLOSED);
    }

    public void sendMessage(HtspMessage htspMessage) {
        Log.d(TAG, "Sending HtspMessage: " + htspMessage.toString());

        mMessageQueue.add(htspMessage);

        try {
            mSocketChannel.register(mSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
            mSelector.wakeup();
        } catch (ClosedChannelException e) {
            Log.w(TAG, "Failed to send message: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(BaseMessage message) {
        sendMessage(message.toHtspMessage());
    }

    public int getState() {
        return mState;
    }

    protected void setState(int state) {
        Log.d(TAG, String.format("Transition to state %d from %d", state, mState));

        if (state == mState) {
            Log.e(TAG, "Attempted to setState to the current state");
            return;
        }

        int previousState = mState;
        mState = state;

        if (mHTSPConnectionListeners != null) {
            for (IConnectionListener listener : mHTSPConnectionListeners) {
                listener.onStateChange(state, previousState);
            }
        }
    }

    private void processConnectableSelectionKey() throws IOException {
        Log.v(TAG, "processConnectableSelectionKey()");

        if (mSocketChannel.isConnectionPending()) {
            mSocketChannel.finishConnect();
        }

        mSocketChannel.register(mSelector, SelectionKey.OP_READ);
    }

    private void processReadableSelectionKey() throws IOException {
        Log.v(TAG, "processReadableSelectionKey()");

        int bufferStartPosition = mReadBuffer.position();
        int bytesRead = this.mSocketChannel.read(mReadBuffer);

        Log.v(TAG, "Read " + bytesRead + " bytes.");

        int bytesToBeConsumed = bufferStartPosition + bytesRead;

        if (bytesRead == -1) {
            close(STATE_FAILED);
        } else if (bytesRead > 0) {
            int bytesConsumed = -1;

            while (mRunning && bytesConsumed != 0 && bytesToBeConsumed > 0) {
                bytesConsumed = processMessage(bytesToBeConsumed);
                bytesToBeConsumed = bytesToBeConsumed - bytesConsumed;
            }
        }
    }

    private int processMessage(int bytesToBeConsumed) throws IOException {
        Log.v(TAG, "Processing a HTSP Message");

        ResponseMessage message = HtspMessage.fromWire(mReadBuffer);
        int bytesConsumed = mReadBuffer.position();

        if (message == null) {
            return 0;
        }

        // Reset the buffers limit to the full amount of data read
        mReadBuffer.limit(bytesToBeConsumed);

        // Compact the buffer, discarding all previously read data, and ensuring we don't
        // loose any bytes already read for the next message.
        mReadBuffer.compact();

        if (mMessageListeners != null) {
            for (IMessageListener listener : mMessageListeners) {
                listener.onMessage(message);
            }
        } else {
            Log.w(TAG, "Message received, but no listeners.. Discarding.");
        }

        return bytesConsumed;
    }

    private void processWritableSelectionKey() throws IOException {
        Log.v(TAG, "processWritableSelectionKey()");
        HtspMessage htspMessage = mMessageQueue.poll();

        if (htspMessage != null) {
            mSocketChannel.write(htspMessage.toWire());
        }
    }
}
