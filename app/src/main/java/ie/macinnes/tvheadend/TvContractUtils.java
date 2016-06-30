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
import android.util.SparseArray;

import java.util.List;

import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.model.Program;
import ie.macinnes.tvheadend.model.ProgramList;

public class TvContractUtils {
    private static final String TAG = TvContractUtils.class.getName();

    public static String getInputId() {
        ComponentName componentName = new ComponentName(
                "ie.macinnes.tvheadend",
                ".tvinput.TvInputService");

        return TvContract.buildInputId(componentName);
    }

    public static Channel getChannelFromChannelUri(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();

        // TODO: Handle when more than 1, or 0 results come back
        try (Cursor cursor = resolver.query(channelUri, null, null,null, null)) {
            return ChannelList.fromCursor(cursor).get(0);
        }
    }

    public static ChannelList getChannels(Context context, String[] projection) {
        Uri channelsUri = TvContract.buildChannelsUriForInput(getInputId());

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            return ChannelList.fromCursor(cursor);
        }
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

    public static ProgramList getPrograms(Context context, Channel channel) {
        Uri channelUri = TvContract.buildChannelUri(channel.getId());
        Uri programsUri = TvContract.buildProgramsUriForChannel(channelUri);

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(programsUri, null, null, null, null)) {
            return ProgramList.fromCursor(cursor);
        }
    }

    public static ProgramList getPrograms(Context context, Uri channelUri) {
        return getPrograms(context, getChannelFromChannelUri(context, channelUri));
    }

    public static SparseArray<Long> buildChannelMap(Context context, ChannelList channelList) {
        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Long> channelMap = new SparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(TvContractUtils.getInputId());
        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID};

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                channelMap.put(originalNetworkId, rowId);
            }
        }

        return channelMap;
    }

    public static Program getCurrentProgram(Context context, Uri channelUri) {
        ContentResolver resolver = context.getContentResolver();

        ProgramList programs = getPrograms(context, channelUri);

        long nowMs = System.currentTimeMillis();

        for (Program program : programs) {
            if (program.getStartTimeUtcMillis() <= nowMs && program.getEndTimeUtcMillis() > nowMs) {
                return program;
            }
        }

        return null;
    }
}
