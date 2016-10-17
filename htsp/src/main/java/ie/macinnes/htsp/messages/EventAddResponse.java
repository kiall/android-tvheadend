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

import ie.macinnes.htsp.HtspMessage;

public class EventAddResponse extends BaseEventResponse {
    static {
        HtspMessage.addMessageResponseType("eventAdd", EventAddResponse.class);
    }

    protected Long mEventId;
    protected Long mChannelId;
    protected Long mStart;
    protected Long mStop;
    protected String mTitle;
    protected String mSubTitle;
    protected String mSummary;
    protected String mDescription;

    @Override
    public Long getEventId() {
        return mEventId;
    }

    @Override
    public void setEventId(Long eventId) {
        mEventId = eventId;
    }

    public Long getChannelId() {
        return mChannelId;
    }

    public void setChannelId(Long channelId) {
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

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setEventId(htspMessage.getLong("eventId"));
        setChannelId(htspMessage.getLong("channelId"));
        setStart(htspMessage.getLong("start"));
        setStop(htspMessage.getLong("stop"));
        setTitle(htspMessage.getString("title"));
        setSubTitle(htspMessage.getString("subtitle"));
        setSummary(htspMessage.getString("summary"));
        setDescription(htspMessage.getString("description"));
    }
}
