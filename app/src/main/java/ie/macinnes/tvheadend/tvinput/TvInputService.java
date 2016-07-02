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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.migrate.MigrateUtils;


public class TvInputService extends android.media.tv.TvInputService {
    private static final String TAG = TvInputService.class.getName();

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private String mSessionType;

    @Override
    public void onCreate() {
        super.onCreate();

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        // TODO: Find a better (+ out of UI thread) way to do this.
        MigrateUtils.doMigrate(getBaseContext());

        // Store the chosen session type
        SharedPreferences sharedPreferences = getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);

        mSessionType = sharedPreferences.getString(Constants.KEY_SESSION, Constants.MEDIA_PLAYER);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mHandlerThread.quit();
        mHandlerThread = null;
        mHandler = null;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.d(TAG, "Creating new TvInputService Session for input ID: " + inputId + ".");

        if (mSessionType != null && mSessionType.equals(Constants.VLC)) {
            return new VlcSession(this, mHandler);
        } else if (mSessionType != null && mSessionType.equals(Constants.EXO_PLAYER)) {
            return new DemoPlayerSession(this, mHandler);
        } else {
            return new MediaPlayerSession(this, mHandler);
        }
    }

}
