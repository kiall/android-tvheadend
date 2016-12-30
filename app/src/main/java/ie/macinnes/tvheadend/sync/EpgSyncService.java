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

package ie.macinnes.tvheadend.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.htsp.Connection;
import ie.macinnes.htsp.ConnectionListener;
import ie.macinnes.htsp.tasks.GetFileTask;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.account.AccountUtils;

public class EpgSyncService extends Service {
    private static final String TAG = EpgSyncService.class.getName();

    protected Context mContext;
    protected HandlerThread mHandlerThread;
    protected Handler mHandler;

    protected AccountManager mAccountManager;
    protected Account mAccount;

    protected boolean mConnectionReady;

    protected Connection mConnection;
    protected Thread mConnectionThread;
    protected EpgSyncTask mEpgSyncTask;
    protected GetFileTask mGetFileTask;

    protected static List<Runnable> sInitialSyncCompleteCallbacks = new ArrayList<>();

    protected final Runnable mInitialSyncCompleteCallback = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Initial Sync Complete");

            for (Runnable r : sInitialSyncCompleteCallbacks) {
                r.run();
            }
        }
    };

    public static void addInitialSyncCompleteCallback(Runnable runnable) {
        if (sInitialSyncCompleteCallbacks.contains(runnable)) {
            Log.w(TAG, "Attempted to add duplicate initial sync complete runnable");
            return;
        }
        sInitialSyncCompleteCallbacks.add(runnable);
    }

    public static void removeInitialSyncCompleteCallback(Runnable runnable) {
        if (!sInitialSyncCompleteCallbacks.contains(runnable)) {
            Log.w(TAG, "Attempted to remove unknown initial sync complete runnable");
            return;
        }
        sInitialSyncCompleteCallbacks.remove(runnable);
    }

    public EpgSyncService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Binding not allowed");
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Starting EPG Sync Service");

        mContext = getApplicationContext();
        mHandlerThread = new HandlerThread("EpgSyncService Handler Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mAccountManager = AccountManager.get(mContext);
        mAccount = AccountUtils.getActiveAccount(mContext);

        openConnection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mAccount == null) {
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stopping EPG Sync Service");

        closeConnection();

        mHandlerThread.quit();
        mHandlerThread.interrupt();
        mHandlerThread = null;
    }

    protected void openConnection() {
        mConnectionReady = false;

        if (!MiscUtils.isNetworkAvailable(mContext)) {
            Log.i(TAG, "No network available, shutting down EPG Sync Service");
            stopSelf();
            return;
        }

        if (mAccount == null) {
            Log.i(TAG, "No account configured, aborting startup of EPG Sync Service");
            stopSelf();
            return;
        }

        initHtspConnection();
    }

    protected void initHtspConnection() {
        final String hostname = mAccountManager.getUserData(mAccount, Constants.KEY_HOSTNAME);
        final int port = Integer.parseInt(mAccountManager.getUserData(mAccount, Constants.KEY_HTSP_PORT));
        final String username = mAccount.name;
        final String password = mAccountManager.getPassword(mAccount);

        // 20971520 = 20MB
        // 10485760 = 10MB
        // 1048576  = 1MB
        mConnection = new Connection(hostname, port, username, password, "android-tvheadend (epg)", BuildConfig.VERSION_NAME, 1048576);

        ConnectionListener connectionListener = new ConnectionListener(mHandler) {
            @Override
            public void onStateChange(int state, int previous) {
                if (state == Connection.STATE_CONNECTING) {
                    Log.d(TAG, "HTSP Connection Connecting");
                } else if (state == Connection.STATE_CONNECTED) {
                    Log.d(TAG, "HTSP Connection Connected");
                } else if (state == Connection.STATE_AUTHENTICATING) {
                    Log.d(TAG, "HTSP Connection Authenticating");
                } else if (state == Connection.STATE_READY) {
                    Log.d(TAG, "HTSP Connection Ready");
                    installTasks();
                    enableAsyncMetadata();
                } else if (state == Connection.STATE_FAILED) {
                    Log.e(TAG, "HTSP Connection Failed, Reconnecting");
                    closeConnection();
                    openConnection();
                } else if (state == Connection.STATE_CLOSED) {
                    Log.i(TAG, "HTSP Connection Closed, shutting down EpgSync Service");
                    cleanupConnection();
                    stopSelf();
                }
            }
        };

        mConnection.addConnectionListener(connectionListener);

        mConnectionThread = new Thread(mConnection);
        mConnectionThread.start();
    }

    protected void installTasks() {
        Log.d(TAG, "Adding GetFileTask");
        mGetFileTask = new GetFileTask(mContext, mHandler);
        mConnection.addMessageListener(mGetFileTask);

        Log.d(TAG, "Adding EpgSyncTask");
        mEpgSyncTask = new EpgSyncTask(mContext, mHandler, mAccount, mGetFileTask);
        mConnection.addMessageListener(mEpgSyncTask);
    }

    protected void enableAsyncMetadata() {
        Log.d(TAG, "Enabling Async Metadata");
        mEpgSyncTask.enableAsyncMetadata(mInitialSyncCompleteCallback);
    }

    protected void closeConnection() {
        if (mConnection != null) {
            Log.d(TAG, "Closing HTSP connection");
            mConnection.close();
        }

        if (mConnectionThread != null) {
            Log.d(TAG, "Waiting for HTSP Connection Thread to finish");

            try {
                mConnectionThread.join();
                Log.d(TAG, "HTSP Connection Thread has finished");
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for HTSP Connection Thread to finish");
            }
        }

        cleanupConnection();
    }

    protected void cleanupConnection() {
        mConnection = null;
        mConnectionThread = null;
        mEpgSyncTask = null;
    }
}
