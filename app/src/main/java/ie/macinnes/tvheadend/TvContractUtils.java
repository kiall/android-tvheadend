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
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseArray;

public class TvContractUtils {
    private static final String TAG = TvContractUtils.class.getName();

    public static final long INVALID_CHANNEL_ID = -1;
    public static final long INVALID_RECORDED_PROGRAM_ID = -1;

    private TvContractUtils() {
        throw new IllegalAccessError("Utility class");
    }

    public static String getInputId() {
        ComponentName componentName = new ComponentName(
                "ie.macinnes.tvheadend",
                ".tvinput.TvInputService");

        return TvContract.buildInputId(componentName);
    }

    public static long getChannelId(Context context, int channelId) {
        ContentResolver resolver = context.getContentResolver();

        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());

        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID};

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (cursor.getInt(1) == channelId) {
                    return cursor.getLong(0);
                }
            }
        }

        return INVALID_CHANNEL_ID;
    }

    public static String getChannelName(Context context, int channelId) {
        ContentResolver resolver = context.getContentResolver();

        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());

        String[] projection = {TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID, TvContract.Channels.COLUMN_DISPLAY_NAME};

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (cursor.getInt(0) == channelId) {
                    return cursor.getString(1);
                }
            }
        }

        return null;
    }

    public static Uri getChannelUri(Context context, int channelId) {
        long androidChannelId = getChannelId(context, channelId);

        if (androidChannelId != INVALID_CHANNEL_ID) {
            return TvContract.buildChannelUri(androidChannelId);
        }

        return null;
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

    public static SparseArray<Uri> buildChannelUriMap(Context context) {
        ContentResolver resolver = context.getContentResolver();

        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Uri> channelMap = new SparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());
        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID};

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                channelMap.put(originalNetworkId, TvContract.buildChannelUri(rowId));
            }
        }

        return channelMap;
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

    public static Uri getProgramUri(Context context, int channelId, int eventId) {
        // TODO: Cache results...
        ContentResolver resolver = context.getContentResolver();

        long androidChannelId = getChannelId(context, channelId);

        if (androidChannelId == INVALID_CHANNEL_ID) {
            Log.w(TAG, "Failed to fetch programUri, unknown channel");
            return null;
        }

        Uri programsUri = TvContract.buildProgramsUriForChannel(androidChannelId);

        String[] projection = {TvContract.Programs._ID, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA};

        String strEventId = String.valueOf(eventId);

        try (Cursor cursor = resolver.query(programsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (strEventId.equals(cursor.getString(1))) {
                    return TvContract.buildProgramUri(cursor.getLong(0));
                }
            }
        }

        return null;
    }

    public static Integer getTvhEventIdFromProgramUri(Context context, Uri programUri) {
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {TvContract.Programs._ID, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA};

        // TODO: Handle when more than 1, or 0 results come back
        try (Cursor cursor = resolver.query(programUri, projection, null,null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                return cursor.getInt(1);
            }
        }

        return null;
    }

    public static SparseArray<Uri> buildProgramUriMap(Context context) {
        ContentResolver resolver = context.getContentResolver();

        // Create a map from event id to program row ID for existing programs.
        SparseArray<Uri> programMap = new SparseArray<>();

        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());

        String[] channelsProjection = {TvContract.Channels._ID};
        try (Cursor cursor = resolver.query(channelsUri, channelsProjection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                SparseArray<Uri> channelPrgramMap = buildProgramUriMap(context, TvContract.buildChannelUri(cursor.getLong(0)));
                for (int i = 0; i < channelPrgramMap.size(); i++) {
                    int key = channelPrgramMap.keyAt(i);
                    Uri value = channelPrgramMap.valueAt(i);
                    programMap.put(key, value);
                }
            }
        }

        return programMap;
    }

    public static SparseArray<Uri> buildProgramUriMap(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();

        // Create a map from event id to program row ID for existing programs.
        SparseArray<Uri> programMap = new SparseArray<>();

        Uri programsUri = TvContract.buildProgramsUriForChannel(channelUri);
        String[] projection = {TvContract.Programs._ID, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA};

        try (Cursor cursor = resolver.query(programsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int tvhEventId = Integer.valueOf(cursor.getString(1));
                programMap.put(tvhEventId, TvContract.buildChannelUri(rowId));
            }
        }

        return programMap;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static long getRecordedProgramId(Context context, int dvrEntryId) {
        ContentResolver resolver = context.getContentResolver();

        Uri recordedProgramsUri = TvContract.RecordedPrograms.CONTENT_URI;

        String[] projection = {TvContract.RecordedPrograms._ID, TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA};

        try (Cursor cursor = resolver.query(recordedProgramsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (cursor.getInt(1) == dvrEntryId) {
                    return cursor.getLong(0);
                }
            }
        }

        return INVALID_CHANNEL_ID;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static Uri getRecordedProgramUri(Context context, int dvrEntryId) {
        long androidRecordedProgramId = getRecordedProgramId(context, dvrEntryId);

        if (androidRecordedProgramId != INVALID_RECORDED_PROGRAM_ID) {
            return TvContract.buildRecordedProgramUri(androidRecordedProgramId);
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static Integer getTvhDvrEntryIdFromRecordedProgramUri(Context context, Uri recordedProgramUri) {
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {TvContract.RecordedPrograms._ID, TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA};

        // TODO: Handle when more than 1, or 0 results come back
        try (Cursor cursor = resolver.query(recordedProgramUri, projection, null,null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                return cursor.getInt(1);
            }
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static SparseArray<Uri> buildRecordedProgramUriMap(Context context) {
        // Create a map from dvr entry id to program row ID for existing recorded programs.
        ContentResolver resolver = context.getContentResolver();

        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Uri> recordedProgramMap = new SparseArray<>();
        Uri recordedProgramsUri = TvContract.RecordedPrograms.CONTENT_URI;
        String[] projection = {TvContract.RecordedPrograms._ID, TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA};

        try (Cursor cursor = resolver.query(recordedProgramsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int internalProviderData = cursor.getInt(1);
                recordedProgramMap.put(internalProviderData, TvContract.buildRecordedProgramUri(rowId));
            }
        }

        return recordedProgramMap;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void removeRecordedProgram(Context context) {
        Uri recordedProgramsUri = TvContract.RecordedPrograms.CONTENT_URI;

        ContentResolver resolver = context.getContentResolver();

        String[] projection = {Channels._ID};

        try (Cursor cursor = resolver.query(recordedProgramsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                Log.d(TAG, "Deleting recorded program: " + rowId);
                resolver.delete(TvContract.buildRecordedProgramUri(rowId), null, null);
            }
        }
    }
}
