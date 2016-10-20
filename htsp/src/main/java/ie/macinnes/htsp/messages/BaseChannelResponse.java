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

import android.content.ContentValues;
import android.media.tv.TvContract;
import android.text.TextUtils;

import ie.macinnes.htsp.HtspMessage;
import ie.macinnes.htsp.ResponseMessage;

public class BaseChannelResponse extends ResponseMessage {
    public static final int INVALID_INT_VALUE = -1;

    protected int mChannelId;
    protected int mChannelNumber;
    protected int mChannelNumberMinor;
    protected String mChannelName;
    protected String mChannelIcon;

    public int getChannelId() {
        return mChannelId;
    }

    public void setChannelId(int channelId) {
        mChannelId = channelId;
    }

    public int getChannelNumber() {
        return mChannelNumber;
    }

    public void setChannelNumber(int channelNumber) {
        mChannelNumber = channelNumber;
    }

    public int getChannelNumberMinor() {
        return mChannelNumberMinor;
    }

    public void setChannelNumberMinor(int channelNumberMinor) {
        mChannelNumberMinor = channelNumberMinor;
    }

    public String getChannelName() {
        return mChannelName;
    }

    public void setChannelName(String channelName) {
        mChannelName = channelName;
    }

    public String getChannelIcon() {
        return mChannelIcon;
    }

    public void setChannelIcon(String channelIcon) {
        mChannelIcon = channelIcon;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setChannelId(htspMessage.getInt("channelId"));
        setChannelNumber(htspMessage.getInt("channelNumber", INVALID_INT_VALUE));
        setChannelNumberMinor(htspMessage.getInt("channelNumberMinor", INVALID_INT_VALUE));
        setChannelName(htspMessage.getString("channelName", null));
        setChannelIcon(htspMessage.getString("channelIcon", null));
        setChannelName(htspMessage.getString("channelName", null));
    }

    public String toString() {
        return "channelId: " + getChannelId();
    }

    public ContentValues toContentValues(String inputId, String accountName) {
        ContentValues values = new ContentValues();


        values.put(TvContract.Channels.COLUMN_INPUT_ID, inputId);
        values.put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER);

        values.put(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID, mChannelId);

        if (mChannelNumber != INVALID_INT_VALUE && mChannelNumberMinor != INVALID_INT_VALUE) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, mChannelNumber + "." + mChannelNumberMinor);
        } else if (mChannelNumber != INVALID_INT_VALUE) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, mChannelNumber);
        }

        if (!TextUtils.isEmpty(mChannelName)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, mChannelName);
        }

        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, accountName);

        return values;
    }
}
