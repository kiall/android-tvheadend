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

public class EnableAsyncMetadataRequest extends RequestMessage {
    public static final String METHOD = "enableAsyncMetadata";

    static {
        HtspMessage.addMessageRequestType(METHOD, EnableAsyncMetadataRequest.class);

        // Force registration of Additional Response Types
        new TagAddResponse();
        new TagUpdateResponse();
        new TagDeleteResponse();
        new ChannelAddResponse();
        new ChannelUpdateResponse();
        new ChannelDeleteResponse();
        new EventAddResponse();
        new EventUpdateResponse();
        new EventDeleteResponse();
        new DvrEntryAddResponse();
        new DvrEntryUpdateResponse();
        new DvrEntryDeleteResponse();
        new InitialSyncCompletedResponse();
    }

    protected boolean mEpg;
    protected long mLastUpdate;
    protected long mEpgMaxTime;
    protected String mLanguage;

    public boolean getEpg() {
        return mEpg;
    }

    public void setEpg(boolean epg) {
        mEpg = epg;
    }

    public long getLastUpdate() {
        return mLastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        mLastUpdate = lastUpdate;
    }

    public long getEpgMaxTime() {
        return mEpgMaxTime;
    }

    public void setEpgMaxTime(long epgMaxTime) {
        mEpgMaxTime = epgMaxTime;
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

        htspMessage.putBoolean("epg", getEpg());
        htspMessage.putLong("lastUpdate", getLastUpdate());
        htspMessage.putLong("epgMaxTime", getEpgMaxTime());
        htspMessage.putString("language", getLanguage());

        return htspMessage;
    }

    @Override
    protected Class<? extends ResponseMessage> getResponseClass() {
        return EnableAsyncMetadataResponse.class;
    }
}
