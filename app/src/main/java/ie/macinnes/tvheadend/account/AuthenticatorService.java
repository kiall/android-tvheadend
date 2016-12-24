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
package ie.macinnes.tvheadend.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.sync.EpgSyncService;

public class AuthenticatorService extends Service {
    private static final String TAG = AuthenticatorService.class.getName();

    private AccountManager mAccountManager;
    private Account[] mCurrentAccounts;

    protected SharedPreferences mSharedPreferences;

    private OnAccountsUpdateListener mAccountsUpdateListener = new OnAccountsUpdateListener() {
        @Override
        public void onAccountsUpdated(Account[] accounts) {
            for (Account currentAccount : mCurrentAccounts) {
                boolean accountExists = false;

                for (Account account : accounts) {
                    if (!account.type.equals(Constants.ACCOUNT_TYPE)) {
                        // This isn't a TVHeadend account, move on..
                        continue;
                    }
                    if (account.equals(currentAccount)) {
                        accountExists = true;
                        break;
                    }
                }

                if (!accountExists && currentAccount.type.equals(Constants.ACCOUNT_TYPE)) {
                    Log.d(TAG, "Account Removed: " + currentAccount.toString());

                    // Stop the EPG Sync Service
                    getBaseContext().stopService(new Intent(getBaseContext(), EpgSyncService.class));

                    // Remove all the channels we added
                    TvContractUtils.removeChannels(getApplicationContext());

                    // Indicate we've not completed the setup
                    MiscUtils.setSetupComplete(getBaseContext(), false);

                    // Discard the previously saved last EPG update stamp
                    mSharedPreferences.edit().remove(Constants.KEY_EPG_LAST_UPDATE).apply();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        mAccountManager = AccountManager.get(this);
        mCurrentAccounts = AccountUtils.getAllAccounts(this);

        mSharedPreferences = getBaseContext().getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        AccountUtils.addOnAccountsUpdatedListener(this, mAccountsUpdateListener, new Handler(), true);
    }

    @Override
    public void onDestroy() {
        mAccountManager.removeOnAccountsUpdatedListener(mAccountsUpdateListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Authenticator authenticator = new Authenticator(this);
        return authenticator.getIBinder();
    }
}
