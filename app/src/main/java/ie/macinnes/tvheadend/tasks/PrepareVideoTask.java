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

package ie.macinnes.tvheadend.tasks;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.TvContractUtils;

public class PrepareVideoTask extends AsyncTask<Void, Void, MediaPlayer> {
    public static final String TAG = PrepareVideoTask.class.getSimpleName();

    private final Context mContext;
    private final Uri mChannelUri;
    private final long mTimeout;

    private Channel mChannel;
    private Throwable mError;
    private boolean prepared;

    protected PrepareVideoTask(Context context, Uri channelUri, long timeout) {
        Log.d(TAG, "Prepare video task created for " + channelUri.toString());

        mContext = context;
        mChannelUri = channelUri;
        mTimeout = timeout;

    }

    @Override
    protected MediaPlayer doInBackground(Void... params) {
        Log.d(TAG, "Started play video task created for " + mChannelUri.toString());

        if (isCancelled()) {
            return null;
        }

        // Gather Details on the Channel
        mChannel = TvContractUtils.getChannelFromChannelUri(mContext, mChannelUri);
        String channelUuid = mChannel.getInternalProviderData().getUuid();

        // Gather Details on the TVHeadend Instance
        AccountManager accountManager = AccountManager.get(mContext);;
        Account[] accounts = accountManager.getAccountsByType("ie.macinnes.tvheadend");

        // TODO: We should only every have one account.. Figure out how that works (or 1 account per
        //       hostname+port combo?)
        Account account = accounts[0];

        String username = account.name;
        String password = accountManager.getPassword(account);
        String hostname = accountManager.getUserData(account, Constants.KEY_HOSTNAME);
        String port = accountManager.getUserData(account, Constants.KEY_PORT);

        // Create authentication headers and streamUri
        Map<String, String> headers = createBasicAuthHeader(username, password);
        Uri videoUri = Uri.parse("http://" + hostname + ":" + port + "/stream/channel/" + channelUuid + "?profile=tif");

        // Prepare the media player
        return prepareMediaPlayer(videoUri, headers);
    }

    private MediaPlayer prepareMediaPlayer(Uri videoUri, Map<String, String> headers) {
        final Object prepareLock = new Object();

        // Create and prep the MediaPlayer
        MediaPlayer mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "MediaPlayer error: " + what + ". Extra = " + extra);
                return true;
            }
        });

        mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                Log.d(TAG, "Video buffering: " + percent + "%");
            }
        });

        mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Log.d(TAG, "Video info: " + what + ", Extra = " + extra);
                return false;
            }
        });

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "MediaPlayer video prepared");
                prepared = true;
                synchronized (prepareLock) {
                    prepareLock.notifyAll();
                }
            }
        });

        try {
            mediaPlayer.setDataSource(mContext, videoUri, headers);

            Log.d(TAG, "Preparing video: " + videoUri + ".");
            mediaPlayer.prepareAsync();

            synchronized (prepareLock) {
                prepareLock.wait(mTimeout);
                if (!prepared) {
                    throw new InterruptedException("Video prepare timed out after " + mTimeout + " ms.");
                }
            }

            return mediaPlayer;

        } catch (Throwable e) {
            Log.e(TAG, "Error preparing video: " + e);

            mError = e;
            mediaPlayer.release();

            return null;
        }
    }

    private Map<String, String> createBasicAuthHeader(String username, String password) {
        Map<String, String> headerMap = new HashMap<String, String>();

        String credentials = username + ":" + password;
        String base64EncodedCredentials =
                Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        headerMap.put("Authorization", "Basic " + base64EncodedCredentials);

        return headerMap;
    }
}
