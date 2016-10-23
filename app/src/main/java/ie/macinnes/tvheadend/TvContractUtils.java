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
package ie.macinnes.tvheadend;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.util.Log;

public class TvContractUtils {
    private static final String TAG = TvContractUtils.class.getName();

    public static String getInputId() {
        ComponentName componentName = new ComponentName(
                "ie.macinnes.tvheadend",
                ".tvinput.TvInputService");

        return TvContract.buildInputId(componentName);
    }

    public static Integer getTvhChannelIdFromChannelUri(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};

        // TODO: Handle when more than 1, or 0 results come back
        try (Cursor cursor = resolver.query(channelUri, projection, null,null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                return cursor.getInt(1);
            }
        }

        return null;
    }

    public static void removeChannels(Context context) {
        Uri channelsUri = TvContract.buildChannelsUriForInput(getInputId());

        ContentResolver resolver = context.getContentResolver();

        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                Log.d(TAG, "Deleting channel: " + rowId);
                resolver.delete(TvContract.buildChannelUri(rowId), null, null);
            }
        }
    }

}
