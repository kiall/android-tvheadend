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
import ie.macinnes.htsp.ResponseMessage;

public class HelloResponse extends ResponseMessage {
    static {
        HtspMessage.addMessageResponseType("hello", HelloResponse.class);
    }

    protected int mHtspVersion;
    protected String mServerName;
    protected String mServerVersion;
    protected String[] mServerCapability;
    protected byte[] mChallenge;
    protected String mWebRoot;

    public int getHtspVersion() {
        return mHtspVersion;
    }

    public void setHtspVersion(int htspVersion) {
        mHtspVersion = htspVersion;
    }

    public String getServerName() {
        return mServerName;
    }

    public void setServerName(String serverName) {
        mServerName = serverName;
    }

    public String getServerVersion() {
        return mServerVersion;
    }

    public void setServerVersion(String serverVersion) {
        mServerVersion = serverVersion;
    }

    public String[] getServerCapability() {
        return mServerCapability;
    }

    public void setServerCapability(String[] serverCapability) {
        mServerCapability = serverCapability;
    }

    public byte[] getChallenge() {
        return mChallenge;
    }

    public void setChallenge(byte[] challenge) {
        mChallenge = challenge;
    }

    public String getWebRoot() {
        return mWebRoot;
    }

    public void setWebRoot(String webRoot) {
        mWebRoot = webRoot;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setHtspVersion(htspMessage.getInt("htspversion"));
        setServerName(htspMessage.getString("servername"));
        setServerVersion(htspMessage.getString("serverversion"));
        setServerCapability(htspMessage.getStringArray("servercapability"));
        setChallenge(htspMessage.getByteArray("challenge"));
        setWebRoot(htspMessage.getString("webroot"));
    }

    public String toString() {
        return "Sequence: " + getSeq() + " HTSP Version: " + getHtspVersion() + " ServerName: " + getServerName();
    }
}
