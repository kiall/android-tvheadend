/* Copyright 2016 Kiall Mac Innes <kiall@macinnes.ie>

Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain
a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations
under the License.
*/
package ie.macinnes.tvheadend.tvinput;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import ie.macinnes.htsp.HtspConnection;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.sync.EpgSyncService;


public class TvInputService extends android.media.tv.TvInputService {
    private static final String TAG = TvInputService.class.getName();

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private String mSessionType;

    private SimpleHtspConnection mConnection;

    private AccountManager mAccountManager;
    private Account mAccount;

    @Override
    public void onCreate() {
        super.onCreate();

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        // Fetch the chosen session type
        SharedPreferences sharedPreferences = getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        mSessionType = sharedPreferences.getString(Constants.KEY_SESSION, Constants.SESSION_MEDIA_PLAYER);

        mAccountManager = AccountManager.get(this);
        mAccount = AccountUtils.getActiveAccount(this);

        openConnection();

        // Start the EPG Sync Service
        getApplicationContext().startService(new Intent(getApplicationContext(), EpgSyncService.class));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        closeConnection();

        mHandlerThread.quit();
        mHandlerThread = null;
        mHandler = null;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.d(TAG, "Creating new TvInputService Session for input ID: " + inputId + ".");

        if (mSessionType != null && mSessionType.equals(Constants.SESSION_VLC)) {
            return new VlcSession(this, mHandler);
        } else if (mSessionType != null && mSessionType.equals(Constants.SESSION_EXO_PLAYER)) {
            return new ExoPlayerSession(this, mHandler, mConnection);
        } else {
            return new MediaPlayerSession(this, mHandler);
        }
    }

    protected void openConnection() {
        if (!MiscUtils.isNetworkAvailable(this)) {
            Log.i(TAG, "No network available, shutting down TV Input Service");
            return;
        }

        if (mAccount == null) {
            Log.i(TAG, "No account configured, aborting startup of TV Input Service");
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
                hostname, port, username, password, "android-tvheadend (TV)",
                BuildConfig.VERSION_NAME);

        mConnection = new SimpleHtspConnection(connectionDetails);
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
