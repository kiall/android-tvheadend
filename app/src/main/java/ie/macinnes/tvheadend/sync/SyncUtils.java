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
package ie.macinnes.tvheadend.sync;


import android.accounts.Account;
import android.content.ContentResolver;
import android.os.Bundle;
import android.util.Log;

import ie.macinnes.tvheadend.Constants;

public class SyncUtils {
    private static final String TAG = SyncUtils.class.getName();

    public static final long SYNC_FREQUENCY_SEC = 60 * 60 * 12 ;  // twice daily

    public static void setUpPeriodicSync(Account account) {
        Log.d(TAG, "Setting periodic sync for account: " + account.toString());

        ContentResolver.setIsSyncable(account, Constants.CONTENT_AUTHORITY, 1);
        ContentResolver.setSyncAutomatically(account, Constants.CONTENT_AUTHORITY, true);

        Bundle bundle = new Bundle();

        ContentResolver.addPeriodicSync(
                account, Constants.CONTENT_AUTHORITY, bundle, SYNC_FREQUENCY_SEC);
    }

    public static void removePeriodicSync(Account account) {
        ContentResolver.setIsSyncable(account, Constants.CONTENT_AUTHORITY, 0);
        ContentResolver.setSyncAutomatically(account, Constants.CONTENT_AUTHORITY, false);

        Bundle bundle = new Bundle();

        ContentResolver.removePeriodicSync(account, Constants.CONTENT_AUTHORITY, bundle);
    }

    public static void requestSync(Account account, boolean quickSync) {
        Log.d(TAG, "Requesting immediate sync for account: " + account.toString());
        ContentResolver.setIsSyncable(account, Constants.CONTENT_AUTHORITY, 1);

        Bundle bundle = new Bundle();

        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putBoolean(Constants.SYNC_EXTRAS_QUICK, quickSync);

        ContentResolver.requestSync(account, Constants.CONTENT_AUTHORITY, bundle);
    }

    public static void requestSync(Account account) {
        requestSync(account, false);
    }
}
