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
import android.graphics.Bitmap;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ie.macinnes.tvheadend.Constants;

public class TVHClient {
    private static final String TAG = TVHClient.class.getName();

    private static final int DEFAULT_CHANNEL_LIMIT = 10000;
    public static final int DEFAULT_EVENT_LIMIT = 1000;
    public static final int QUICK_EVENT_LIMIT = 10;

    private static TVHClient sInstance;
    private final Context mContext;
    private RequestQueue mRequestQueue;

    private String mAccountHostname;
    private String mAccountPort;
    private String mAccountPath;
    private String mAccountName;
    private String mAccountPassword;

    private int mTimeout = 30;

    public static synchronized TVHClient getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TVHClient(context);
        }
        return sInstance;
    }

    public TVHClient(Context context) {
        mContext = context;
    }

    public void setConnectionInfo(Account account) {
        AccountManager accountManager = AccountManager.get(mContext);

        String password = accountManager.getPassword(account);
        String hostname = accountManager.getUserData(account, Constants.KEY_HOSTNAME);
        String port = accountManager.getUserData(account, Constants.KEY_HTTP_PORT);
        String path = accountManager.getUserData(account, Constants.KEY_HTTP_PATH);

        setConnectionInfo(hostname, port, path, account.name, password);
    }

    public void setConnectionInfo(String accountHostname, String accountPort, String accountPath, String accountName, String accountPassword) {
        mAccountHostname = accountHostname;
        mAccountPort = accountPort;
        mAccountPath = accountPath;
        mAccountName = accountName;
        mAccountPassword = accountPassword;
    }

    private RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext());
        }
        return mRequestQueue;
    }

    public String getBaseHttpUri() {
        if (mAccountPath == null) {
            return "http://" + mAccountHostname + ":" + mAccountPort;
        } else {
            return "http://" + mAccountHostname + ":" + mAccountPort + "/" + mAccountPath;
        }
    }

    public void getServerInfo(Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        Log.d(TAG, "Calling getServerInfo");

        String url = getBaseHttpUri() + "/api/serverinfo";

        JsonObjectRequest jsObjRequest = new JsonObjectRequest(
                Request.Method.GET, url, null, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(jsObjRequest);
    }

    public JSONObject getServerInfo() throws InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();

        getServerInfo(future, future);

        return future.get(mTimeout, TimeUnit.SECONDS);
    }

    public void getProfileList(Response.Listener<KeyValList> listener, Response.ErrorListener errorListener) {
        Log.d(TAG, "Calling getProfileList");

        String url = getBaseHttpUri() + "/api/profile/list";

        GsonRequest<KeyValList> request = new GsonRequest<KeyValList>(
                Request.Method.GET, url, KeyValList.class, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(request);
    }

    public KeyValList getProfileList() throws InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<KeyValList> future = RequestFuture.newFuture();

        getProfileList(future, future);

        return future.get(mTimeout, TimeUnit.SECONDS);
    }

    public void getChannelGrid(Response.Listener<ChannelList> listener, Response.ErrorListener errorListener, int channelLimit) {
        Log.d(TAG, "Calling getChannelGrid");

        String url = getBaseHttpUri() + "/api/channel/grid?limit=" + Integer.toString(channelLimit);

        GsonRequest<ChannelList> request = new GsonRequest<ChannelList>(
                Request.Method.GET, url, ChannelList.class, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(request);
    }

    public void getChannelGrid(Response.Listener<ChannelList> listener, Response.ErrorListener errorListener) {
        getChannelGrid(listener, errorListener, DEFAULT_CHANNEL_LIMIT);
    }

    public ChannelList getChannelGrid(int channelLimit) throws InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<ChannelList> future = RequestFuture.newFuture();

        getChannelGrid(future, future, channelLimit);

        return future.get(mTimeout, TimeUnit.SECONDS);
    }

    public ChannelList getChannelGrid() throws InterruptedException, ExecutionException, TimeoutException {
        return getChannelGrid(DEFAULT_CHANNEL_LIMIT);
    }

    public void getChannelIcon(Response.Listener<Bitmap> listener, Response.ErrorListener errorListener, String channelIconPath) {
        Log.d(TAG, "Calling getChannelIcon");

        String url = getBaseHttpUri() + "/" + channelIconPath;

        ImageRequest request = new ImageRequest(
                url, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(request);
    }

    public Bitmap getChannelIcon(String channelIconPath) throws InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<Bitmap> future = RequestFuture.newFuture();

        getChannelIcon(future, future, channelIconPath);

        return future.get(mTimeout, TimeUnit.SECONDS);
    }

    public void getEventGrid(Response.Listener<EventList> listener, Response.ErrorListener errorListener, String channelUuid, int eventLimit) {
        Log.d(TAG, "Calling getEventGrid for channel: " + channelUuid);

        String url = getBaseHttpUri() + "/api/epg/events/grid?limit=" + Integer.toString(eventLimit) + "&channel=" + channelUuid;

        GsonRequest<EventList> request = new GsonRequest<EventList>(
                Request.Method.GET, url, EventList.class, listener, errorListener, mAccountName, mAccountPassword);

        getRequestQueue().add(request);
    }

    public void getEventGrid(Response.Listener<EventList> listener, Response.ErrorListener errorListener, String channelUuid) {
        getEventGrid(listener, errorListener, channelUuid, DEFAULT_EVENT_LIMIT);
    }

    public EventList getEventGrid(String channelUuid, int eventLimit) throws InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<EventList> future = RequestFuture.newFuture();

        getEventGrid(future, future, channelUuid, eventLimit);

        return future.get(mTimeout, TimeUnit.SECONDS);
    }

    public EventList getEventGrid(String channelUuid) throws InterruptedException, ExecutionException, TimeoutException {
        return getEventGrid(channelUuid, DEFAULT_EVENT_LIMIT);
    }

    public static class KeyVal {
        public String key;
        public String value;
    }

    public static class KeyValList {
        public ArrayList<KeyVal> entries;
    }

    public static class Channel {
        public String uuid;
        public Boolean enabled;
        public String name;
        public String number;
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
        public String channelNumber;
        public long start;
        public long stop;
        public String title;
        public String subtitle;
        public String summary;
        public String description;
        public int seasonNumber;
        public int episodeNumber;
        public String image;
        public int nextEventId;
    }

    public static class EventList {
        public ArrayList<Event> entries;
    }
}
