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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import ie.macinnes.htsp.HtspConnection;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.tvheadend.BuildConfig;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.sync.EpgSyncService;


public class TvInputService extends android.media.tv.TvInputService {
    private static final String TAG = TvInputService.class.getName();

    private SimpleHtspConnection mConnection;

    private AccountManager mAccountManager;
    private Account mAccount;

    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();

        mSharedPreferences = getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        mAccountManager = AccountManager.get(this);
        mAccount = AccountUtils.getActiveAccount(this);

        openConnection();
        maybeEnableDvr();

        // Start the EPG Sync Service
        getApplicationContext().startService(new Intent(getApplicationContext(), EpgSyncService.class));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        closeConnection();
    }

    @Nullable
    @Override
    public Session onCreateSession(String inputId) {
        Log.d(TAG, "Creating new TvInputService LiveSession for input ID: " + inputId + ".");

        return new LiveSession(this, mConnection);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Nullable
    @Override
    public RecordingSession onCreateRecordingSession(String inputId) {
        Log.d(TAG, "Creating new TvInputService HtspRecordingSession for input ID: " + inputId + ".");

        return new HtspRecordingSession(this, mConnection);
    }

    protected void maybeEnableDvr() {
        boolean dvrEnabled = mSharedPreferences.getBoolean(
                Constants.KEY_DVR_ENABLED,
                getResources().getBoolean(R.bool.pref_default_dvr_enabled));

        if (dvrEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            ComponentName componentName = new ComponentName(this, TvInputService.class);
            TvInputInfo tvInputInfo = new TvInputInfo.Builder(this, componentName)
                    .setCanRecord(true)
                    .setTunerCount(10)
                    .build();
            tim.updateTvInputInfo(tvInputInfo);
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
