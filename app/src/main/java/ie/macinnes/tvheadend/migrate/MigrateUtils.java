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
package ie.macinnes.tvheadend.migrate;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.account.AccountUtils;


public class MigrateUtils {
    public static final String TAG = MigrateUtils.class.getName();
    private static final int VERSION_14 = 14;
    private static final int VERSION_38 = 38;
    private static final int VERSION_79 = 79;

    public static void doMigrate(Context context) {
        Log.d(TAG, "doMigrate()");

        // Set all default values
        PreferenceManager.setDefaultValues(context, Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE,
                                           R.xml.preferences, true);

        // Store the current version
        int currentApplicationVersion = Constants.MIGRATE_VERSION;

        // Store the last migrated version
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        int lastInstalledApplicationVersion = sharedPreferences.getInt(
                Constants.KEY_APP_VERSION, 0);

        Log.i(TAG, "Migrate from " + lastInstalledApplicationVersion + " to " + currentApplicationVersion);

        // Run any migrations
        if (currentApplicationVersion != lastInstalledApplicationVersion) {
            if (lastInstalledApplicationVersion <= VERSION_14) {
                migrateAccountsPortName(context);
            }
            if (lastInstalledApplicationVersion <= VERSION_38) {
                migrateAccountHtspPort(context);
            }
            if (lastInstalledApplicationVersion <= VERSION_79) {
                migrateSetupCompleted(context);
            }
        }

        // Store the current version as the last installed version
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(Constants.KEY_APP_VERSION, currentApplicationVersion);
        editor.apply();
    }

    protected static void migrateSetupCompleted(Context context) {
        Log.d(TAG, "migrateSetupCompleted()");

        Account account = AccountUtils.getActiveAccount(context);

        if (account != null) {
            // We have an account, so lets assume the user completed the setup.
            MiscUtils.setSetupComplete(context, true);
        }
    }

    protected static void migrateAccountsPortName(Context context) {
        Log.d(TAG, "migrateAccountsPortData()");

        AccountManager accountManager = AccountManager.get(context);
        Account[] accounts = AccountUtils.getAllAccounts(context);

        for (Account account : accounts) {
            String port = accountManager.getUserData(account, "PORT");

            if (port != null) {
                accountManager.setUserData(account, Constants.KEY_HTTP_PORT, port);
                accountManager.setUserData(account, "PORT", null);
            }
        }
    }

    protected static void migrateAccountHtspPort(Context context) {
        Log.d(TAG, "migrateAccountHtspPort()");

        AccountManager accountManager = AccountManager.get(context);
        Account[] accounts = AccountUtils.getAllAccounts(context);

        for (Account account : accounts) {
            String httpPort = accountManager.getUserData(account, Constants.KEY_HTTP_PORT);

            int htspPort = Integer.parseInt(httpPort) + 1;
            accountManager.setUserData(account, Constants.KEY_HTSP_PORT, Integer.toString(htspPort));
        }
    }
}

