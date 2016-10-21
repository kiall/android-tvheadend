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
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.htsp.Connection;
import ie.macinnes.htsp.ConnectionListener;
import ie.macinnes.htsp.tasks.AuthenticateTask;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.account.AccountUtils;

public class EpgSyncService extends Service {
    private static final String TAG = EpgSyncService.class.getName();

    protected Context mContext;
    protected AccountManager mAccountManager;
    protected Account mAccount;

    protected boolean mIsConnected;
    protected boolean mIsAuthenticated;

    protected Connection mConnection;
    protected Thread mConnectionThread;
    protected EpgSyncTask mEpgSyncTask;

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
        openConnection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stopping EPG Sync Service");
        closeConnection();
    }

    protected void openConnection() {
        mIsConnected = false;

        mContext = getApplicationContext();
        mAccountManager = AccountManager.get(mContext);
        mAccount = AccountUtils.getActiveAccount(mContext);

        if (mAccount == null) {
            Log.i(TAG, "No account configured, aborting startup of EPG Sync Service");
            stopSelf();
        }

        initHtspConnection();
        enableAsyncMetadata();
    }

    protected void initHtspConnection() {
        final Object connectionLock = new Object();

        final String hostname = mAccountManager.getUserData(mAccount, Constants.KEY_HOSTNAME);
        final int port = Integer.parseInt(mAccountManager.getUserData(mAccount, Constants.KEY_HTSP_PORT));
        final String username = mAccount.name;
        final String password = mAccountManager.getPassword(mAccount);

        // 20971520 = 20MB
        // 10485760 = 10MB
        // 1048576  = 1MB
        mConnection = new Connection(hostname, port, 100000);

        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onStateChange(int state, int previous) {
                synchronized (connectionLock) {
                    if (state == Connection.STATE_CONNECTING) {
                        Log.d(TAG, "HTSP Connection Connecting");
                    } else if (state == Connection.STATE_CONNECTED) {
                        Log.d(TAG, "HTSP Connection Connected");
                        mIsConnected = true;
                        connectionLock.notifyAll();
                    } else if (state == Connection.STATE_FAILED) {
                        Log.d(TAG, "HTSP Connection Failed, Reconnection");
                        closeConnection();
                        openConnection();
                    } else if (state == Connection.STATE_CLOSED) {
                        Log.d(TAG, "HTSP Connection Closed");
                        mIsConnected = false;
                        mConnection = null;
                        connectionLock.notifyAll();
                    }
                }
            }
        };

        mConnection.addConnectionListener(connectionListener);

        mConnectionThread = new Thread(mConnection);
        mConnectionThread.start();

        synchronized (connectionLock) {
            try {
                connectionLock.wait(5000);
                if (!mIsConnected) {
                    Log.d(TAG, "HTSP Connection timed out, Aborting");
                    return;
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "HTSP Connection Interrupted, Aborting");
                return;
            }
        }

        // We're connected, let's authenticate.
        final Object authenticationLock = new Object();

        final AuthenticateTask.IAuthenticateTaskCallback authenticateTaskCallback = new AuthenticateTask.IAuthenticateTaskCallback() {
            @Override
            public void onSuccess() {
                synchronized (authenticationLock) {
                    Log.d(TAG, "Authentication successful");
                    mIsAuthenticated = true;
                    authenticationLock.notifyAll();
                }
            }

            @Override
            public void onFailure() {
                Log.d(TAG, "Authentication failed");
                mIsAuthenticated = false;
                authenticationLock.notifyAll();
            }
        };

        final String versionName = MiscUtils.getAppVersionName(mContext);
        final AuthenticateTask authenticateTask = new AuthenticateTask(
                username, password, "android-tvheadend (epg)", versionName);

        mConnection.addMessageListener(authenticateTask);
        authenticateTask.authenticate(authenticateTaskCallback);

        synchronized (authenticationLock) {
            try {
                authenticationLock.wait(5000);
                if (!mIsAuthenticated) {
                    Log.d(TAG, "HTSP Authentication failed, Aborting");
                    return;
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "HTSP Authentication Interrupted, Aborting");
                return;
            }
        }
    }

    protected void enableAsyncMetadata() {
        Log.d(TAG, "Enabling Async Metadata");
        mEpgSyncTask = new EpgSyncTask(mContext, mAccount);
        mConnection.addMessageListener(mEpgSyncTask);
        mEpgSyncTask.enableAsyncMetadata(mInitialSyncCompleteCallback);
    }

    protected void closeConnection() {
        Log.d(TAG, "Closing HTSP connection");
        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
        }

        mEpgSyncTask = null;
    }
}
