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
package ie.macinnes.tvheadend.client;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;

import java.util.ArrayList;

import ie.macinnes.tvheadend.Constants;

public class TVHClient {
    private static final String TAG = TVHClient.class.getName();

    private static TVHClient mInstance;
    private final Context mContext;
    private RequestQueue mRequestQueue;

    private String mAccountHostname;
    private String mAccountPort;
    private String mAccountName;
    private String mAccountPassword;

    public static synchronized TVHClient getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TVHClient(context);
        }
        return mInstance;
    }

    public TVHClient(Context context) {
        mContext = context;
    }

    public void setConnectionInfo(Account account) {
        AccountManager accountManager = AccountManager.get(mContext);

        String password = accountManager.getPassword(account);
        String hostname = accountManager.getUserData(account, Constants.KEY_HOSTNAME);
        String port = accountManager.getUserData(account, Constants.KEY_PORT);

        setConnectionInfo(hostname, port, account.name, password);
    }

    public void setConnectionInfo(String accountHostname, String accountPort, String accountName, String accountPassword) {
        mAccountHostname = accountHostname;
        mAccountPort = accountPort;
        mAccountName = accountName;
        mAccountPassword = accountPassword;
    }

    private RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext());
        }
        return mRequestQueue;
    }

    public void getServerInfo(Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        Log.d(TAG, "Calling getServerInfo");

        String url = "http://" + mAccountHostname + ":" + mAccountPort + "/api/serverinfo";

        AuthenticatedJsonObjectRequest jsObjRequest = new AuthenticatedJsonObjectRequest(
                Request.Method.GET, url, null, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(jsObjRequest);
    }

    public void getChannelGrid(Response.Listener<ChannelList> listener, Response.ErrorListener errorListener) {
        Log.d(TAG, "Calling getChannelGrid");

        String url = "http://" + mAccountHostname + ":" + mAccountPort + "/api/channel/grid?limit=10000";

        GsonRequest<ChannelList> request = new GsonRequest<ChannelList>(
                Request.Method.GET, url, ChannelList.class, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(request);
    }

    public void getEventGrid(Response.Listener<EventList> listener, Response.ErrorListener errorListener, String channelUuid) {
        Log.d(TAG, "Calling getEventGrid for channel: " + channelUuid);

        String url = "http://" + mAccountHostname + ":" + mAccountPort + "/api/epg/events/grid?limit=10000&channel=" + channelUuid;

        GsonRequest<EventList> request = new GsonRequest<EventList>(
                Request.Method.GET, url, EventList.class, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(request);
    }

    public static class Channel {
        public String uuid;
        public Boolean enabled;
        public String name;
        public int number;
        @SerializedName("icon_public_url")
        public String icon_url;
        public String bouquet;
    }

    public static class ChannelList {
        public ArrayList<Channel> entries;
    }

    public static class Event {
        public String eventId;
        public String channelUuid;
        public int channelNumber;
        public long start;
        public long stop;
        public String title;
        public String subtitle;
        public String summary;
        public int nextEventId;
    }

    public static class EventList {
        public ArrayList<Event> entries;
    }
}
