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

public class GetEventsResponse extends ResponseMessage {
    static {
        HtspMessage.addMessageResponseType("getEvents", GetEventsResponse.class);
    }

    protected EventAddResponse[] mEvents;

    public Object[] getEvents() {
        return mEvents;
    }

    public void setEvents(EventAddResponse[] events) {
        mEvents = events;
    }

    public void fromHtspMessage(HtspMessage htspMessage) {
        super.fromHtspMessage(htspMessage);

        HtspMessage[] htspMessages = htspMessage.getHtspMessageArray("events");

        EventAddResponse[] events = new EventAddResponse[htspMessages.length];

        int i = 0;

        for (HtspMessage m : htspMessages) {
            events[i] = new EventAddResponse();
            events[i].fromHtspMessage(m);
            i++;
        }

        setEvents(events);
    }

    public String toString() {
        return "Event Count: " + mEvents.length;
    }
}
