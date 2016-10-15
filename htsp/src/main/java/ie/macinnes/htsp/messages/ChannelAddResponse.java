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

public class ChannelAddResponse extends BaseChannelResponse {
    static {
        HtspMessage.addMessageResponseType("channelAdd", ChannelAddResponse.class);
    }

    protected Long mChannelNumber;
    protected Long mChannelNumberMinor;
    protected String mChannelName;
    protected String mChannelIcon;

    public Long getChannelNumber() {
        return mChannelNumber;
    }

    public void setChannelNumber(Long channelNumber) {
        mChannelNumber = channelNumber;
    }

    public Long getChannelNumberMinor() {
        return mChannelNumberMinor;
    }

    public void setChannelNumberMinor(Long channelNumberMinor) {
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

        setChannelNumber(htspMessage.getLong("channelNumber"));
        setChannelNumberMinor(htspMessage.getLong("channelNumberMinor"));
        setChannelName(htspMessage.getString("channelName"));
        setChannelIcon(htspMessage.getString("channelIcon"));
        setChannelName(htspMessage.getString("channelName"));
    }

    public String toString() {
        return "channelId: " + getChannelId() + " / channelName: " + getChannelName();
    }
}
