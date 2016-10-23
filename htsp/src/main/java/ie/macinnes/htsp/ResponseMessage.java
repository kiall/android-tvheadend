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

public class ResponseMessage extends BaseMessage {
    public static final int INVALID_SEQ = -1;

    protected int mSeq;
    protected String mError;

    public int getSeq() {
        return mSeq;
    }

    public void setSeq(int seq) {
        mSeq = seq;
    }

    public String getError() {
        return mError;
    }

    public void setError(String error) {
        mError = error;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setSeq(htspMessage.getInt("seq", INVALID_INT_VALUE));
        setError(htspMessage.getString("error", null));
    }
}
