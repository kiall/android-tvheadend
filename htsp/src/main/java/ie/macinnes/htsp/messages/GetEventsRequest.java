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
import ie.macinnes.htsp.RequestMessage;
import ie.macinnes.htsp.ResponseMessage;

public class GetEventsRequest extends RequestMessage {
    public static final String METHOD = "getEvents";

    static {
        HtspMessage.addMessageRequestType(METHOD, GetEventsRequest.class);
    }

    protected Long mEventId;
    protected Long mChannelId;
    protected Long mNumFollowing;
    protected Long mMaxTime;
    protected String mLanguage;

    public Long getEventId() {
        return mEventId;
    }

    public void setEventId(Long eventId) {
        mEventId = eventId;
    }

    public Long getChannelId() {
        return mChannelId;
    }

    public void setChannelId(Long channelId) {
        mChannelId = channelId;
    }

    public Long getNumFollowing() {
        return mNumFollowing;
    }

    public void setNumFollowing(Long numFollowing) {
        mNumFollowing = numFollowing;
    }

    public Long getMaxTime() {
        return mMaxTime;
    }

    public void setMaxTime(Long maxTime) {
        mMaxTime = maxTime;
    }

    public String getLanguage() {
        return mLanguage;
    }

    public void setLanguage(String language) {
        mLanguage = language;
    }

    @Override
    public HtspMessage toHtspMessage() {
        HtspMessage htspMessage = super.toHtspMessage();

        htspMessage.putString("method", METHOD);

        htspMessage.putLong("eventId", getEventId());
        htspMessage.putLong("channelId", getChannelId());
        htspMessage.putLong("numFollowing", getNumFollowing());
        htspMessage.putLong("maxTime", getMaxTime());
        htspMessage.putString("language", getLanguage());

        return htspMessage;
    }

    @Override
    protected Class<? extends ResponseMessage> getResponseClass() {
        return GetEventsResponse.class;
    }
}
