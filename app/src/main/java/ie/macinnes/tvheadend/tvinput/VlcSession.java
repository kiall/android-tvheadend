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
package ie.macinnes.tvheadend.tvinput;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Map;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.client.ClientUtils;
import ie.macinnes.tvheadend.model.Channel;

public class VlcSession extends BaseSession {
    private static final String TAG = VlcSession.class.getName();

    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;

    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public VlcSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");

        ArrayList<String> options = new ArrayList<>();
        options.add("--http-reconnect");
        options.add("--network-caching=2000");

        mLibVLC = new LibVLC(options);
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurfaces (" + mSessionNumber + ")");

        mSurface = surface;

        if (mMediaPlayer != null && mSurface != null) {
            mMediaPlayer.getVLCVout().setVideoSurface(surface, null);
            mMediaPlayer.getVLCVout().attachViews();
        }

        return true;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");

        mVolume = volume;

        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume((int) mVolume * 100);
        }
    }

    protected boolean playChannel(Channel channel) {
        // Stop any existing playback
        stopPlayback();

        // Gather Details on the Channel
        String channelUuid = channel.getInternalProviderData().getUuid();

        // Gather Details on the TVHeadend Instance
        AccountManager accountManager = AccountManager.get(mContext);;
        Account account = AccountUtils.getActiveAccount(mContext);

        String username = account.name;
        String password = accountManager.getPassword(account);
        String hostname = accountManager.getUserData(account, Constants.KEY_HOSTNAME);
        String httpPort = accountManager.getUserData(account, Constants.KEY_HTTP_PORT);

        // Create authentication headers and streamUri
        Map<String, String> headers = ClientUtils.createBasicAuthHeader(username, password);
        Uri videoUri = Uri.parse("http://" + username + ":" + password + "@" + hostname + ":" + httpPort + "/stream/channel/" + channelUuid + "?profile=tif");

        // Prepare the media player
        mMediaPlayer = prepareMediaPlayer(videoUri, headers);

        // Start the media playback
        Log.d(TAG, "Starting playback of channel: " + channel.toString());
        mMediaPlayer.play();
        notifyVideoAvailable();

        return mMediaPlayer != null;
    }

    protected void stopPlayback() {
        Log.d(TAG, "Session stopPlayback (" + mSessionNumber + ")");

        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

    }

    private MediaPlayer prepareMediaPlayer(Uri videoUri, Map<String, String> headers) {
        // Create and prep the MediaPlayer instance
        MediaPlayer mediaPlayer = new MediaPlayer(mLibVLC);

        try {
            Log.d(TAG, "Preparing video: " + videoUri + ".");

            Media currentMedia = new Media(mLibVLC, videoUri);
            mediaPlayer.setMedia(currentMedia);

        } catch (Throwable e) {
            Log.e(TAG, "Error preparing video: " + e);

            mediaPlayer.release();

            return null;
        }

        mediaPlayer.getVLCVout().setVideoSurface(mSurface, null);
        mediaPlayer.getVLCVout().attachViews();
        mediaPlayer.setVolume((int) mVolume * 100);
        mediaPlayer.getAudioTracks();

        return mediaPlayer;
    }
}
