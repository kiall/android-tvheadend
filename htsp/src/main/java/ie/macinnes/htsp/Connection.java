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

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import ie.macinnes.htsp.tasks.AuthenticateTask;

public class Connection implements Runnable {
    private static final String TAG = Connection.class.getName();

    public final static int STATE_CLOSED = 0;
    public final static int STATE_CONNECTING = 1;
    public final static int STATE_CONNECTED = 2;
    public final static int STATE_AUTHENTICATING = 3;
    public final static int STATE_READY = 4;
    public final static int STATE_CLOSING = 5;
    public final static int STATE_FAILED = 6;

    protected SocketChannel mSocketChannel;
    protected Selector mSelector;

    protected Lock mLock;

    protected ByteBuffer mReadBuffer;
    protected Queue<HtspMessage> mMessageQueue;

    protected boolean mRunning = false;
    protected boolean mAuthenticated = false;
    protected int mState = STATE_CLOSED;

    protected String mHostname;
    protected int mPort;

    protected List<IConnectionListener> mHTSPConnectionListeners = new ArrayList<>();
    protected List<IMessageListener> mMessageListeners = new ArrayList<>();

    protected AuthenticateTask mAuthenticateTask;

    public Connection(String hostname, int port, String username, String password, String clientName, String clientVersion) {
        // 1048576 = 1 MB
        this(hostname, port, username, password, clientName, clientVersion, 1048576);
    }

    public Connection(String hostname, int port, String username, String password, String clientName, String clientVersion, int bufferSize) {
        mHostname = hostname;
        mPort = port;

        mLock = new ReentrantLock();

        mMessageQueue = new LinkedList<>();
        mReadBuffer = ByteBuffer.allocate(bufferSize);

        mAuthenticateTask = new AuthenticateTask(username, password, clientName, clientVersion);
    }

    public void addConnectionListener(IConnectionListener listener) {
        if (mHTSPConnectionListeners.contains(listener)) {
            Log.w(TAG, "Attempted to add duplicate connection listener");
            return;
        }
        listener.setConnection(this);
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
        } catch (ConnectionException e) {
            Log.e(TAG, "Failed to open HTSP connection", e);
            return;
        }

        authenticate();

        mRunning = true;

        while (mRunning) {
            try {
                mSelector.select();
            } catch (IOException e) {
                Log.e(TAG, "Failed to select from socket channel", e);
                close(STATE_FAILED);
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

                if (mSocketChannel != null) {
                    if (mSocketChannel.isConnected() && mMessageQueue.isEmpty()) {
                        mSocketChannel.register(mSelector, SelectionKey.OP_READ);
                    } else if (mSocketChannel.isConnected()) {
                        mSocketChannel.register(mSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Something failed - shutting down", e);
                close(STATE_FAILED);
            }
        }

        if (!isClosed()) {
            close();
        }
    }

    protected void open() throws ConnectionException {
        Log.i(TAG, "Opening HTSP Connection");

        mLock.lock();

        try {
            if (mSocketChannel != null) {
                throw new RuntimeException("Attempted to open HTSP connection twice");
            }

            setState(STATE_CONNECTING);

            final Object openLock = new Object();

            try {
                mSocketChannel = SocketChannel.open();
                mSocketChannel.connect(new InetSocketAddress(mHostname, mPort));
                mSocketChannel.configureBlocking(false);
                mSelector = Selector.open();
            } catch (IOException e) {
                Log.e(TAG, "Caught IOException while opening SocketChannel: " + e.getLocalizedMessage());
                close(STATE_FAILED);
                throw new ConnectionException(e.getLocalizedMessage(), e);
            } catch (UnresolvedAddressException e) {
                Log.e(TAG, "Failed to resolve HTSP server address: " + e.getLocalizedMessage());
                close(STATE_FAILED);
                throw new ConnectionException(e);
            }

            try {
                mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT, openLock);
            } catch (ClosedChannelException e) {
                Log.e(TAG, "Failed to register selector, channel closed: " + e.getLocalizedMessage());
                close(STATE_FAILED);
                throw new ConnectionException(e.getLocalizedMessage(), e);
            }

            synchronized (openLock) {
                try {
                    openLock.wait(2000);
                    if (mSocketChannel.isConnectionPending()) {
                        Log.e(TAG, "Failed to register selector, timeout");
                        close(STATE_FAILED);
                        throw new ConnectionException("Timeout while registering selector");
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Failed to register selector, interrupted");
                    close(STATE_FAILED);
                    throw new ConnectionException(e.getLocalizedMessage(), e);
                }
            }

            Log.i(TAG, "HTSP Connected");
            setState(STATE_CONNECTED);
        } finally {
            mLock.unlock();
        }
    }

    protected void authenticate() {
        setState(STATE_AUTHENTICATING);

        addMessageListener(mAuthenticateTask);

        final AuthenticateTask.IAuthenticateTaskCallback authenticateTaskCallback = new AuthenticateTask.IAuthenticateTaskCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "HTSP Authentication successful");
                mAuthenticated = false;

                Log.i(TAG, "HTSP Ready");
                setState(STATE_READY);
            }

            @Override
            public void onFailure() {
                mAuthenticated = false;
                Log.e(TAG, "HTSP Authentication failed");
                close(STATE_FAILED);
            }
        };

        mAuthenticateTask.authenticate(authenticateTaskCallback);
    }

    public void close() {
        close(STATE_CLOSED);
    }

    protected void close(int endState) {
        mLock.lock();
        try {
            if (getState() == STATE_CLOSED) {
                Log.i(TAG, "Connection already closed, ignoring close request");
                return;
            }

            if (getState() == STATE_CLOSING) {
                Log.i(TAG, "Connection already closing, ignoring close request");
                return;
            }

            if (getState() == STATE_FAILED) {
                Log.i(TAG, "Connection already failed, ignoring close request");
                return;
            }

            Log.i(TAG, "Closing HTSP Connection, End State: " + endState);

            mRunning = false;
            setState(STATE_CLOSING);

            // Clear out any pending messages
            mMessageQueue.clear();

            if (mSocketChannel != null) {
                try {
                    Log.i(TAG, "Calling SocketChannel close");
                    mSocketChannel.socket().close();
                    mSocketChannel.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close socket channel: " + e.getLocalizedMessage());
                } finally {
                    mSocketChannel = null;
                }
            }

            if (mSelector != null) {
                try {
                    Log.w(TAG, "Calling Selector close");
                    mSelector.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close socket channel: " + e.getLocalizedMessage());
                } finally {
                    mSelector = null;
                }
            }

            // Wipe the read buffer
            mReadBuffer.clear();
            mReadBuffer = null;

            setState(endState);
        } finally {
            mLock.unlock();
        }
    }

    public void sendMessage(HtspMessage htspMessage) {
        Log.d(TAG, "Sending HtspMessage: " + htspMessage.toString());

        mLock.lock();
        try {
            mMessageQueue.add(htspMessage);
            mSocketChannel.register(mSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
            mSelector.wakeup();
        } catch (ClosedChannelException e) {
            Log.w(TAG, "Failed to send message: " + e.getLocalizedMessage());
            e.printStackTrace();
        } finally {
            mLock.unlock();
        }
    }

    public void sendMessage(BaseMessage message) {
        sendMessage(message.toHtspMessage());
    }

    public boolean isClosed() {
        return getState() == STATE_CLOSED || getState() == STATE_FAILED;
    }

    public int getState() {
        return mState;
    }

    protected void setState(final int state) {
        Log.d(TAG, String.format("Transition to state %d from %d", state, mState));

        if (state == mState) {
            Log.e(TAG, "Attempted to setState to the current state");
            return;
        }

        final int previousState = mState;
        mState = state;

        if (mHTSPConnectionListeners != null) {
            for (final IConnectionListener listener : mHTSPConnectionListeners) {
                Handler handler = listener.getHandler();
                if (handler != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onStateChange(state, previousState);
                        }
                    });
                } else {
                    listener.onStateChange(state, previousState);
                }
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
            Log.e(TAG, "Failed to process readable selection key, read -1 bytes");
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

        final ResponseMessage message = HtspMessage.fromWire(mReadBuffer);
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
            for (final IMessageListener listener : mMessageListeners) {
                Handler handler = listener.getHandler();
                if (handler != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onMessage(message);
                        }
                    });
                } else {
                    listener.onMessage(message);
                }
            }
        } else {
            Log.w(TAG, "Message received, but no listeners.. Discarding.");
        }

        return bytesConsumed;
    }

    private void processWritableSelectionKey() throws IOException {
        Log.v(TAG, "processWritableSelectionKey()");
        HtspMessage htspMessage;

        try {
            htspMessage = mMessageQueue.poll();
        } catch (NoSuchElementException e) {
            // According to the java.util.Queue javadoc, poll should never emit this exception.
            // The docs for the remove() method say: Retrieves and removes the head of this queue.
            // This method differs from poll() only in that it throws an exception if this queue is
            // empty.
            // Yet, Somehow I'm seeing this exception raised.
            htspMessage = null;
            Log.w(TAG, "processWritableSelectionKey received a unexpected NoSuchElementException" +
                       " despite JavaDoc saying it's not possible");
        }
        if (htspMessage != null) {
            mSocketChannel.write(htspMessage.toWire());
        }
    }
}
