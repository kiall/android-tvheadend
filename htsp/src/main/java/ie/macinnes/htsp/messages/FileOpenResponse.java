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

public class FileOpenResponse extends ResponseMessage {
    static {
        HtspMessage.addMessageResponseType("fileOpen", FileOpenResponse.class);
    }

    protected int mId;
    protected long mSize;
    protected long mMtime;

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }

    public long getSize() {
        return mSize;
    }

    public void setSize(long size) {
        mSize = size;
    }

    public long getMtime() {
        return mMtime;
    }

    public void setMtime(long mtime) {
        mMtime = mtime;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        setId(htspMessage.getInt("id", INVALID_INT_VALUE));
        setSize(htspMessage.getLong("size", INVALID_LONG_VALUE));
        setMtime(htspMessage.getLong("mtime", INVALID_LONG_VALUE));
    }

    public String toString() {
        return "FileOpen: " + getId();
    }
}
