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
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.MiscUtils;
import ie.macinnes.tvheadend.account.AccountUtils;


public class MigrateUtils {
    public static final String TAG = MigrateUtils.class.getName();
    private static final int VERSION_79 = 79;
    private static final int VERSION_80 = 80;
    private static final int VERSION_81 = 81;

    public static void doMigrate(Context context) {
        Log.d(TAG, "doMigrate()");

        // Lookup the current version
        int currentApplicationVersion = Constants.MIGRATE_VERSION;

        // Lookup the last migrated version
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        int lastInstalledApplicationVersion = sharedPreferences.getInt(
                Constants.KEY_APP_VERSION, 0);

        Log.i(TAG, "Migrate from " + lastInstalledApplicationVersion + " to " + currentApplicationVersion);

        // Run any migrations
        if (currentApplicationVersion != lastInstalledApplicationVersion) {
            if (lastInstalledApplicationVersion <= VERSION_79) {
                migrateSetupCompleted(context);
            }
            if (lastInstalledApplicationVersion <= VERSION_80) {
                migrateMediaPlayerVlcRemoval(context);
            }
            if (lastInstalledApplicationVersion <= VERSION_81) {
                migrateExoPlayerHttpRemoval(context);
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

    protected static void migrateMediaPlayerVlcRemoval(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("SESSION");
        editor.remove("vlc_deinterlace_enabled");
        editor.remove("vlc_deinterlace_method");
        editor.commit();
    }
    protected static void migrateExoPlayerHttpRemoval(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("http_stream_profile");
        editor.commit();
    }
}

