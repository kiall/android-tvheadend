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
package ie.macinnes.tvheadend.model;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.util.Objects;

import ie.macinnes.tvheadend.client.TVHClient;

public class Program implements Comparable<Program> {
    private static final long INVALID_LONG_VALUE = -1;
    private static final int INVALID_INT_VALUE = -1;

    private long mProgramId;
    private long mChannelId;
    private String mTitle;
    private String mEpisodeTitle;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mShortDescription;
    private String mLongDescription;
    private String mSeasonDisplayNumber;
    private String mEpisodeDisplayNumber;

    private InternalProviderData mInternalProviderData;

    public long getProgramId() {
        return mProgramId;
    }

    public void setProgramId(long programId) {
        mProgramId = programId;
    }

    public long getChannelId() {
        return mChannelId;
    }

    public void setChannelId(long channelId) {
        mChannelId = channelId;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getEpisodeTitle() {
        return mEpisodeTitle;
    }

    public void setEpisodeTitle(String episodeTitle) {
        mEpisodeTitle = episodeTitle;
    }

    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    public void setStartTimeUtcMillis(long startTimeUtcMillis) {
        mStartTimeUtcMillis = startTimeUtcMillis;
    }

    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    public void setEndTimeUtcMillis(long endTimeUtcMillis) {
        mEndTimeUtcMillis = endTimeUtcMillis;
    }

    public String getShortDescription() {
        return mShortDescription;
    }

    public void setShortDescription(String shortDescription) {
        mShortDescription = shortDescription;
    }

    public String getLongDescription() {
        return mLongDescription;
    }

    public void setLongDescription(String longDescription) {
        mLongDescription = longDescription;
    }

    public String getSeasonDisplayNumber() {
        return mSeasonDisplayNumber;
    }

    public void setSeasonDisplayNumber(String seasonDisplayNumber) {
        mSeasonDisplayNumber = seasonDisplayNumber;
    }

    public String getEpisodeDisplayNumber() {
        return mEpisodeDisplayNumber;
    }

    public void setEpisodeDisplayNumber(String episodeDisplayNumber) {
        mEpisodeDisplayNumber = episodeDisplayNumber;
    }

    public InternalProviderData getInternalProviderData() {
        return mInternalProviderData;
    }

    public void setInternalProviderData(InternalProviderData internalProviderData) {
        mInternalProviderData = internalProviderData;
    }

    @TargetApi(24)
    public static Program fromCursor(Cursor cursor) {
        Program program = new Program();

        int index = cursor.getColumnIndex(TvContract.Programs._ID);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setProgramId(cursor.getLong(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_CHANNEL_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setChannelId(cursor.getLong(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_TITLE);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setTitle(cursor.getString(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_EPISODE_TITLE);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setEpisodeTitle(cursor.getString(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setStartTimeUtcMillis(cursor.getLong(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setEndTimeUtcMillis(cursor.getLong(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_SHORT_DESCRIPTION);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setShortDescription(cursor.getString(index));
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_LONG_DESCRIPTION);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setLongDescription(cursor.getString(index));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            index = cursor.getColumnIndex(TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER);
            if (index >= 0 && !cursor.isNull(index)) {
                program.setSeasonDisplayNumber(cursor.getString(index));
            }

            index = cursor.getColumnIndex(TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER);
            if (index >= 0 && !cursor.isNull(index)) {
                program.setEpisodeDisplayNumber(cursor.getString(index));
            }
        } else {
            index = cursor.getColumnIndex(TvContract.Programs.COLUMN_SEASON_NUMBER);
            if (index >= 0 && !cursor.isNull(index)) {
                program.setSeasonDisplayNumber(cursor.getString(index));
            }

            index = cursor.getColumnIndex(TvContract.Programs.COLUMN_EPISODE_NUMBER);
            if (index >= 0 && !cursor.isNull(index)) {
                program.setEpisodeDisplayNumber(cursor.getString(index));
            }
        }

        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA);
        if (index >= 0 && !cursor.isNull(index)) {
            program.setInternalProviderData(InternalProviderData.fromString(cursor.getString(index)));
        }

        return program;
    }

    public static Program fromClientEvent(TVHClient.Event clientEvent, long channelId) {
        Program program = new Program();

        // Set the provided channelId
        program.setChannelId(channelId);

        // Copy values from the clientEvent
        program.setTitle(clientEvent.title);
        program.setEpisodeTitle(clientEvent.subtitle);
        // Use summary as short description, otherwise use description
        if (clientEvent.summary != null) {
            program.setShortDescription(clientEvent.summary);
        } else if (clientEvent.description != null) {
            program.setShortDescription(clientEvent.description);
        }
        // Use description as long description, otherwise use summary
        if (clientEvent.description != null) {
            program.setLongDescription(clientEvent.description);
        } else if (clientEvent.summary != null) {
            program.setLongDescription(clientEvent.summary);
        }
        program.setStartTimeUtcMillis(clientEvent.start * 1000);
        program.setEndTimeUtcMillis(clientEvent.stop * 1000);
        program.setSeasonDisplayNumber(Integer.toString(clientEvent.seasonNumber));
        program.setEpisodeDisplayNumber(Integer.toString(clientEvent.episodeNumber));

        // Prep and set a InternalProviderData object
        InternalProviderData providerData = new InternalProviderData();

        providerData.setEventId(clientEvent.eventId);

        program.setInternalProviderData(providerData);

        return program;
    }

    @TargetApi(24)
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();

        if (mChannelId != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_CHANNEL_ID, mChannelId);
        } else {
            values.putNull(TvContract.Programs.COLUMN_CHANNEL_ID);
        }

        if (!TextUtils.isEmpty(mTitle)) {
            values.put(TvContract.Programs.COLUMN_TITLE, mTitle);
        } else {
            values.putNull(TvContract.Programs.COLUMN_TITLE);
        }

        if (!TextUtils.isEmpty(mEpisodeTitle)) {
            values.put(TvContract.Programs.COLUMN_EPISODE_TITLE, mEpisodeTitle);
        } else {
            values.putNull(TvContract.Programs.COLUMN_EPISODE_TITLE);
        }

        if (!TextUtils.isEmpty(mShortDescription)) {
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, mShortDescription);
        } else {
            values.putNull(TvContract.Programs.COLUMN_SHORT_DESCRIPTION);
        }

        if (!TextUtils.isEmpty(mLongDescription)) {
            values.put(TvContract.Programs.COLUMN_LONG_DESCRIPTION, mLongDescription);
        } else {
            values.putNull(TvContract.Programs.COLUMN_LONG_DESCRIPTION);
        }

        if (mStartTimeUtcMillis != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, mStartTimeUtcMillis);
        } else {
            values.putNull(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS);
        }

        if (mEndTimeUtcMillis != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, mEndTimeUtcMillis);
        } else {
            values.putNull(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!TextUtils.isEmpty(mSeasonDisplayNumber)) {
                values.put(TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER, mSeasonDisplayNumber);
            } else {
                values.putNull(TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER);
            }

            if (!TextUtils.isEmpty(mEpisodeDisplayNumber)) {
                values.put(TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER, mEpisodeDisplayNumber);
            } else {
                values.putNull(TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER);
            }
        } else {
            if (!TextUtils.isEmpty(mSeasonDisplayNumber)) {
                values.put(TvContract.Programs.COLUMN_SEASON_NUMBER, mSeasonDisplayNumber);
            } else {
                values.putNull(TvContract.Programs.COLUMN_SEASON_NUMBER);
            }

            if (!TextUtils.isEmpty(mEpisodeDisplayNumber)) {
                values.put(TvContract.Programs.COLUMN_EPISODE_NUMBER, mEpisodeDisplayNumber);
            } else {
                values.putNull(TvContract.Programs.COLUMN_EPISODE_NUMBER);
            }
        }

        if (mInternalProviderData != null) {
            values.put(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA, mInternalProviderData.toString());
        } else {
            values.putNull(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA);
        }

        return values;
    }

    @Override
    public int compareTo(@NonNull Program other) {
        return Long.compare(mStartTimeUtcMillis, other.mStartTimeUtcMillis);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Program)) {
            return false;
        }
        Program program = (Program) other;
        return mChannelId == program.mChannelId
                && mStartTimeUtcMillis == program.mStartTimeUtcMillis
                && mEndTimeUtcMillis == program.mEndTimeUtcMillis
                && Objects.equals(mTitle, program.mTitle)
                && Objects.equals(mEpisodeTitle, program.mEpisodeTitle)
                && Objects.equals(mShortDescription, program.mShortDescription)
                && Objects.equals(mLongDescription, program.mLongDescription)
                && Objects.equals(mSeasonDisplayNumber, program.mSeasonDisplayNumber)
                && Objects.equals(mEpisodeDisplayNumber, program.mEpisodeDisplayNumber)
                && mInternalProviderData.equals(program.mInternalProviderData);
    }

    public static class InternalProviderData {
        // TODO: Replace with gson store
        private String mEventId;

        public static InternalProviderData fromString(String string) {
            InternalProviderData providerData = new InternalProviderData();
            String[] parts = string.split(":");

            providerData.mEventId = parts[0];

            return providerData;
        }

        public String toString() {
            return mEventId;
        }

        public String getEventId() {
            return mEventId;
        }

        public void setEventId(String eventId) {
            mEventId = eventId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof InternalProviderData)) {
                return false;
            }
            InternalProviderData providerData = (InternalProviderData) other;
            return mEventId == providerData.mEventId;
        }
    }
}
