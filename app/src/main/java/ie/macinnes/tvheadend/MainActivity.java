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
package ie.macinnes.tvheadend;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.util.Log;

import ie.macinnes.tvheadend.settings.SettingsActivity;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getName();

    @Override
    protected void onStart() {
        super.onStart();

        Intent i;

        if (MiscUtils.isSetupComplete(this)) {
            i = SettingsActivity.getPreferencesIntent(this);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                i = new Intent(TvInputManager.ACTION_SETUP_INPUTS);
            } else {
                i = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
                i.setData(TvContract.buildChannelsUriForInput(TvContractUtils.getInputId()));
            }
        }

        startActivity(i);

        finish();
    }
}
