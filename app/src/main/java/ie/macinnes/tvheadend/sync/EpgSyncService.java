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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import ie.macinnes.htsp.HtspConnection;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.account.AccountUtils;

public class EpgSyncService extends Service {
    private static final String TAG = EpgSyncService.class.getName();

    protected HandlerThread mHandlerThread;
    protected Handler mHandler;

    protected SharedPreferences mSharedPreferences;

    protected AccountManager mAccountManager;
    protected Account mAccount;

    protected SimpleHtspConnection mConnection;
    protected EpgSyncTask mEpgSyncTask;

    public EpgSyncService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Binding not allowed");
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Starting EPG Sync Service");

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_TVHEADEND, MODE_PRIVATE);

        if (!MiscUtils.isSetupComplete(this)) {
            Log.i(TAG, "Setup not completed, shutting down EPG Sync Service");
            stopSelf();
            return;
        }

        final boolean enableEpgSync = mSharedPreferences.getBoolean(
                Constants.KEY_EPG_SYNC_ENABLED,
                getResources().getBoolean(R.bool.pref_default_epg_sync_enabled)
        );

        if (!enableEpgSync) {
            Log.i(TAG, "EPG Sync disabled, shutting down EPG Sync Service");
            stopSelf();
            return;
        }

        mHandlerThread = new HandlerThread("EpgSyncService Handler Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mAccountManager = AccountManager.get(this);
        mAccount = AccountUtils.getActiveAccount(this);

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

        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread.interrupt();
            mHandlerThread = null;
        }
    }

    protected void openConnection() {
        if (!MiscUtils.isNetworkAvailable(this)) {
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

        HtspConnection.ConnectionDetails connectionDetails = new HtspConnection.ConnectionDetails(
                hostname, port, username, password, "android-tvheadend (EPG)",
                BuildConfig.VERSION_NAME);

        mConnection = new SimpleHtspConnection(connectionDetails);

        mEpgSyncTask = new EpgSyncTask(this, mConnection);

        mConnection.addMessageListener(mEpgSyncTask);
        mConnection.addAuthenticationListener(mEpgSyncTask);

        mConnection.start();
    }

    protected void closeConnection() {
        if (mConnection != null) {
            Log.d(TAG, "Closing HTSP connection");
            mConnection.stop();
        }

        cleanupConnection();
    }

    protected void cleanupConnection() {
        mConnection = null;
    }
}
