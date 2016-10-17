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

public class QueueStatusResponse extends ResponseMessage {
    static {
        HtspMessage.addMessageResponseType("queueStatus", QueueStatusResponse.class);
    }

    protected Long mSubscriptionId;
    protected Long mPackets;
    protected Long mBytes;
    protected Long mDelay;
    protected Long mBdrops;
    protected Long mPdrops;
    protected Long mIdrops;
    protected Long mDelta;

    public Long getSubscriptionId() {
        return mSubscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        mSubscriptionId = subscriptionId;
    }

    public Long getPackets() {
        return mPackets;
    }

    public void setPackets(Long packets) {
        mPackets = packets;
    }

    public Long getBytes() {
        return mBytes;
    }

    public void setBytes(Long bytes) {
        mBytes = bytes;
    }

    public Long getDelay() {
        return mDelay;
    }

    public void setDelay(Long delay) {
        mDelay = delay;
    }

    public Long getBdrops() {
        return mBdrops;
    }

    public void setBdrops(Long bdrops) {
        mBdrops = bdrops;
    }

    public Long getPdrops() {
        return mPdrops;
    }

    public void setPdrops(Long pdrops) {
        mPdrops = pdrops;
    }

    public Long getIdrops() {
        return mIdrops;
    }

    public void setIdrops(Long idrops) {
        mIdrops = idrops;
    }

    public Long getDelta() {
        return mDelta;
    }

    public void setDelta(Long delta) {
        mDelta = delta;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setSubscriptionId(htspMessage.getLong("subscriptionId"));
        setPackets(htspMessage.getLong("packets"));
        setBytes(htspMessage.getLong("bytes"));
        setDelay(htspMessage.getLong("delay"));
        setBdrops(htspMessage.getLong("Bdrops"));
        setPdrops(htspMessage.getLong("Pdrops"));
        setIdrops(htspMessage.getLong("Idrops"));
        setDelta(htspMessage.getLong("delta"));
    }

    public String toString() {
        return "QueueStatus<subscriptionId: " + getSubscriptionId() + " Packets: " + getPackets() + " Delay: " + getDelay() + ">";
    }
}
