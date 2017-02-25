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

import android.content.Context;
import android.util.Log;

import org.acra.ACRA;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.HttpSender;

import ie.macinnes.tvheadend.migrate.MigrateUtils;

public class Application extends android.app.Application {
    private static final String TAG = Application.class.getName();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // Initialize ACRA crash reporting
        if (BuildConfig.ACRA_ENABLED) {
            Log.i(TAG, "Initializing ACRA");
            try {
                final ACRAConfiguration config = new ConfigurationBuilder(this)
                        .setHttpMethod(HttpSender.Method.PUT)
                        .setReportType(HttpSender.Type.JSON)
                        .setFormUri(BuildConfig.ACRA_REPORT_URI + "/" + BuildConfig.VERSION_CODE)
                        .setLogcatArguments("-t", "1000", "-v", "time", "*:D")
                        .setAdditionalSharedPreferences(Constants.PREFERENCE_TVHEADEND)
                        .setSharedPreferenceName(Constants.PREFERENCE_TVHEADEND)
                        .setSharedPreferenceMode(Context.MODE_PRIVATE)
                        .setBuildConfigClass(BuildConfig.class)
                        .build();
                ACRA.init(this, config);
            } catch (ACRAConfigurationException e) {
                Log.e(TAG, "Failed to init ACRA", e);
            }
        }

        // TODO: Find a better (+ out of UI thread) way to do this.
        MigrateUtils.doMigrate(getBaseContext());
    }
}
