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

package ie.macinnes.htsp;

import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestMessage extends BaseMessage {
    protected static AtomicInteger sSequence = new AtomicInteger();

    protected long mSeq;
    protected String mUsername;
    protected byte[] mDigest;

    public long getSeq() {
        return mSeq;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public byte[] getDigest() {
        return mDigest;
    }

    public void setDigest(byte[] digest) {
        mDigest = digest;
    }

    public RequestMessage() {
        mSeq = sSequence.getAndIncrement();

        Class<? extends ResponseMessage> clazz = getResponseClass();

        if (clazz != null) {
            HtspMessage.addMessageResponseTypeBySeq(mSeq, clazz);
        }
    }

    @Override
    public HtspMessage toHtspMessage() {
        HtspMessage htspMessage = super.toHtspMessage();

        htspMessage.put("seq", getSeq());
        htspMessage.put("username", getUsername());
        htspMessage.put("digest", getDigest());

        return htspMessage;
    }

    protected Class<? extends ResponseMessage> getResponseClass() {
        return null;
    }
}
