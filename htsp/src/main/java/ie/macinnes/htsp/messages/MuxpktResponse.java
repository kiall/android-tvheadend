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

public class MuxpktResponse extends ResponseMessage {
    static {
        HtspMessage.addMessageResponseType("muxpkt", MuxpktResponse.class);
    }

    protected Long mSubscriptionId;
    protected Long mFrameType;
    protected Long mStream;
    protected Long mDts;
    protected Long mPts;
    protected Long mDuration;
    protected byte[] mPayload;

    public Long getSubscriptionId() {
        return mSubscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        mSubscriptionId = subscriptionId;
    }

    public Long getFrameType() {
        return mFrameType;
    }

    public void setFrameType(Long frameType) {
        mFrameType = frameType;
    }

    public Long getStream() {
        return mStream;
    }

    public void setStream(Long stream) {
        mStream = stream;
    }

    public Long getDts() {
        return mDts;
    }

    public void setDts(Long dts) {
        mDts = dts;
    }

    public Long getPts() {
        return mPts;
    }

    public void setPts(Long pts) {
        mPts = pts;
    }

    public Long getDuration() {
        return mDuration;
    }

    public void setDuration(Long duration) {
        mDuration = duration;
    }

    public byte[] getPayload() {
        return mPayload;
    }

    public void setPayload(byte[] payload) {
        mPayload = payload;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setSubscriptionId(htspMessage.getLong("subscriptionId"));
        setFrameType(htspMessage.getLong("frametype"));
        setStream(htspMessage.getLong("stream"));
        setDts(htspMessage.getLong("dts"));
        setPts(htspMessage.getLong("pts"));
        setDuration(htspMessage.getLong("duration"));
        setPayload(htspMessage.getByteArray("payload"));
    }

    public String toString() {
        return "Muxpkt<subscriptionId: " + getSubscriptionId() + " Stream: " + getStream() + " Payload (Length): " + getPayload().length + ">";
    }
}
