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

package ie.macinnes.htsp.messages;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.os.Build;
import android.text.TextUtils;
import android.util.SparseArray;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.ResponseMessage;

public class BaseEventResponse extends ResponseMessage {
    protected int mEventId;
    protected int mChannelId;
    protected Long mStart;
    protected Long mStop;
    protected String mTitle;
    protected String mSubTitle;
    protected String mSummary;
    protected String mDescription;
    // Some fields skipped
    protected int mContentType;
    protected int mAgeRating;
    protected int mSeasonNumber;
    protected int mSeasonCount;
    protected int mEpisodeNumber;
    protected int mEpisodeCount;
    // Some fields skipped
    private String mImage;

    public int getEventId() {
        return mEventId;
    }

    public void setEventId(int eventId) {
        mEventId = eventId;
    }

    public int getChannelId() {
        return mChannelId;
    }

    public void setChannelId(int channelId) {
        mChannelId = channelId;
    }

    public Long getStart() {
        return mStart;
    }

    public void setStart(Long start) {
        mStart = start;
    }

    public Long getStop() {
        return mStop;
    }

    public void setStop(Long stop) {
        mStop = stop;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getSubTitle() {
        return mSubTitle;
    }

    public void setSubTitle(String subTitle) {
        mSubTitle = subTitle;
    }

    public String getSummary() {
        return mSummary;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public String getDescription() {
        return mDescription;
    }
    
    public void setDescription(String description) {
        mDescription = description;
    }
    
    public int getContentType() {
        return mContentType;
    }

    public void setContentType(int contentType) {
        mContentType = contentType;
    }
    
    public int getAgeRating() {
        return mAgeRating;
    }

    public void setAgeRating(int ageRating) {
        mAgeRating = ageRating;
    }

    public int getSeasonNumber() {
        return mSeasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        mSeasonNumber = seasonNumber;
    }

    public int getSeasonCount() {
        return mSeasonCount;
    }

    public void setSeasonCount(int seasonCount) {
        mSeasonCount = seasonCount;
    }

    public int getEpisodeNumber() {
        return mEpisodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        mEpisodeNumber = episodeNumber;
    }

    public int getEpisodeCount() {
        return mEpisodeCount;
    }

    public void setEpisodeCount(int episodeCount) {
        mEpisodeCount = episodeCount;
    }

    public String getImage() {
        return mImage;
    }

    public void setImage(String image) {
        mImage = image;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setEventId(htspMessage.getInt("eventId"));
        setChannelId(htspMessage.getInt("channelId", INVALID_INT_VALUE));
        setStart(htspMessage.getLong("start", INVALID_LONG_VALUE));
        setStop(htspMessage.getLong("stop", INVALID_LONG_VALUE));
        setTitle(htspMessage.getString("title", null));
        setSubTitle(htspMessage.getString("subtitle", null));
        setSummary(htspMessage.getString("summary", null));
        setDescription(htspMessage.getString("description", null));
        // Some fields skipped
        setContentType(htspMessage.getInt("contentType", INVALID_INT_VALUE));
        setAgeRating(htspMessage.getInt("ageRating", INVALID_INT_VALUE));
        setSeasonNumber(htspMessage.getInt("seasonNumber", INVALID_INT_VALUE));
        setSeasonCount(htspMessage.getInt("seasonCount", INVALID_INT_VALUE));
        setEpisodeNumber(htspMessage.getInt("episodeNumber", INVALID_INT_VALUE));
        setEpisodeCount(htspMessage.getInt("episodeCount", INVALID_INT_VALUE));
        // Some fields skipped
        setImage(htspMessage.getString("image", null));
    }

    private static final SparseArray<String> mProgramGenre = new SparseArray<String>() {
        {
            append(16, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(17, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(18, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(19, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(20, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.COMEDY));
            append(21, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(22, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(23, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.DRAMA));
            append(32, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(33, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(34, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(35, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(48, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(49, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(50, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(51, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(64, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(65, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(66, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(67, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(68, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(69, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(70, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(71, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(72, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(73, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(74, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(75, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(80, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(81, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(82, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(82, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(83, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(84, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(85, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(96, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(97, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(98, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(99, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(100, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(101, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(102, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(112, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(113, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(114, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(115, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(116, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(117, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(118, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(118, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(120, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(121, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(122, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(129, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(144, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(145, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ANIMAL_WILDLIFE));
            append(146, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(147, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(148, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(150, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.EDUCATION));
            append(160, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(161, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TRAVEL));
            append(162, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(163, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(164, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(165, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(166, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SHOPPING));
            append(167, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
        }
    };
    
    public String toString() {
        return "eventId: " + getEventId();
    }

    @TargetApi(24)
    public ContentValues toContentValues(long channelId) {
        ContentValues values = new ContentValues();

        values.put(TvContract.Programs.COLUMN_CHANNEL_ID, channelId);
        values.put(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA, String.valueOf(mEventId));

        // COLUMN_TITLE, COLUMN_EPISODE_TITLE, and COLUMN_SHORT_DESCRIPTION are used in the
        // Live Channels app EPG Grid. COLUMN_LONG_DESCRIPTION appears unused.
        // On Sony TVs, COLUMN_LONG_DESCRIPTION is used for the "more info" display.

        if (!TextUtils.isEmpty(mTitle)) {
            // The title of this TV program.
            values.put(TvContract.Programs.COLUMN_TITLE, mTitle);
        }

        if (!TextUtils.isEmpty(mSubTitle)) {
            // The episode title of this TV program for episodic TV shows.
            values.put(TvContract.Programs.COLUMN_EPISODE_TITLE, mSubTitle);
        }

        if (!TextUtils.isEmpty(mSummary) && !TextUtils.isEmpty(mDescription)) {
            // If we have both summary and description... use them both
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, mSummary);
            values.put(TvContract.Programs.COLUMN_LONG_DESCRIPTION, mDescription);

        } else if (!TextUtils.isEmpty(mSummary) && TextUtils.isEmpty(mDescription)) {
            // If we have only summary, use it.
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, mSummary);

        } else if (TextUtils.isEmpty(mSummary) && !TextUtils.isEmpty(mDescription)) {
            // If we have only description, use it.
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, mDescription);
        }
        
        if (mContentType != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_CANONICAL_GENRE, mProgramGenre.get(mContentType));
        }
        
        if (mAgeRating >= 4 && mAgeRating <= 18) {
            TvContentRating rating = TvContentRating.createRating("com.android.tv", "DVB", "DVB_" + mAgeRating);
            values.put(TvContract.Programs.COLUMN_CONTENT_RATING, rating.flattenToString());
        }

        if (mStart != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, mStart * 1000);
        }

        if (mStop != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, mStop * 1000);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (mSeasonNumber != INVALID_INT_VALUE) {
                values.put(TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER, mSeasonNumber);
            }

            if (mEpisodeNumber != INVALID_INT_VALUE) {
                values.put(TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER, mEpisodeNumber);
            }
        } else {
            if (mSeasonNumber != INVALID_INT_VALUE) {
                values.put(TvContract.Programs.COLUMN_SEASON_NUMBER, mSeasonNumber);
            }

            if (mEpisodeNumber != INVALID_INT_VALUE) {
                values.put(TvContract.Programs.COLUMN_EPISODE_NUMBER, mEpisodeNumber);
            }
        }

        if (!TextUtils.isEmpty(mImage)) {
            values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, mImage);
        }

        return values;
    }
}
