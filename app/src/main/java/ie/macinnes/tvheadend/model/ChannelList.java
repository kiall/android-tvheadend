/* Copyright 2016 Kiall Mac Innes <kiall@macinnes.ie>

Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain
a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations
under the License.
*/
package ie.macinnes.tvheadend.model;

import android.database.Cursor;

import java.util.ArrayList;

import ie.macinnes.tvheadend.client.TVHClient;

public class ChannelList extends ArrayList<Channel> {
    // TODO: Remove this
    private String mInputId;

    public static ChannelList fromCursor(Cursor cursor) {
        ChannelList channelList = new ChannelList();

        if (cursor == null || cursor.getCount() == 0) {
            return channelList;
        }

        while (cursor.moveToNext()) {
            channelList.add(Channel.fromCursor(cursor));
        }

        return channelList;
    }

    public static ChannelList fromClientChannelList(TVHClient.ChannelList clientChannelList, String inputId) {
        ChannelList channelList = new ChannelList();

        for (TVHClient.Channel channel : clientChannelList.entries) {
            channelList.add(Channel.fromClientChannel(channel, inputId));
        }

        return channelList;
    }
}
