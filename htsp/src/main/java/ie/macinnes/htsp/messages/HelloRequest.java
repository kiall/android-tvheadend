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

public class HelloRequest extends RequestMessage {
    public static final String METHOD = "hello";

    static {
        HtspMessage.addMessageRequestType(METHOD, HelloRequest.class);
    }

    protected int mHtspVersion;
    protected String mClientName;
    protected String mClientVersion;

    public int getHtspVersion() {
        return mHtspVersion;
    }

    public void setHtspVersion(int htspVersion) {
        mHtspVersion = htspVersion;
    }

    public String getClientName() {
        return mClientName;
    }

    public void setClientName(String clientName) {
        mClientName = clientName;
    }

    public String getClientVersion() {
        return mClientVersion;
    }

    public void setClientVersion(String clientVersion) {
        mClientVersion = clientVersion;
    }

    @Override
    public HtspMessage toHtspMessage() {
        HtspMessage htspMessage = super.toHtspMessage();

        htspMessage.putString("method", METHOD);

        htspMessage.putInt("htspversion", getHtspVersion());
        htspMessage.putString("clientname", getClientName());
        htspMessage.putString("clientversion", getClientVersion());

        return htspMessage;
    }

    @Override
    protected Class<? extends ResponseMessage> getResponseClass() {
        return HelloResponse.class;
    }
}
