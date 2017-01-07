/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ie.macinnes.htspold.messages;

import ie.macinnes.htspold.HtspMessage;
import ie.macinnes.htspold.ResponseMessage;

public class MuxpktResponse extends ResponseMessage {
    static {
        HtspMessage.addMessageResponseType("muxpkt", MuxpktResponse.class);
    }

    protected int mSubscriptionId;
    protected int mFrameType;
    protected int mStream;
    protected long mDts;
    protected long mPts;
    protected int mDuration;
    protected byte[] mPayload;

    public int getSubscriptionId() {
        return mSubscriptionId;
    }

    public void setSubscriptionId(int subscriptionId) {
        mSubscriptionId = subscriptionId;
    }

    public int getFrameType() {
        return mFrameType;
    }

    public void setFrameType(int frameType) {
        mFrameType = frameType;
    }

    public int getStream() {
        return mStream;
    }

    public void setStream(int stream) {
        mStream = stream;
    }

    public long getDts() {
        return mDts;
    }

    public void setDts(long dts) {
        mDts = dts;
    }

    public long getPts() {
        return mPts;
    }

    public void setPts(long pts) {
        mPts = pts;
    }

    public int getDuration() {
        return mDuration;
    }

    public void setDuration(int duration) {
        mDuration = duration;
    }

    public byte[] getPayload() {
        return mPayload;
    }

    public void setPayload(byte[] payload) {
        mPayload = payload;
    }

    public int getPayloadLength() {
        return mPayload.length;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setSubscriptionId(htspMessage.getInt("subscriptionId"));
        setFrameType(htspMessage.getInt("frametype"));
        setStream(htspMessage.getInt("stream"));
        setDts(htspMessage.getLong("dts"));
        setPts(htspMessage.getLong("pts"));
        setDuration(htspMessage.getInt("duration"));
        setPayload(htspMessage.getByteArray("payload"));
    }

    public String toString() {
        return "Muxpkt<subscriptionId: " + getSubscriptionId() + " Stream: " + getStream() + " Payload (Length): " + getPayload().length + ">";
    }
}
