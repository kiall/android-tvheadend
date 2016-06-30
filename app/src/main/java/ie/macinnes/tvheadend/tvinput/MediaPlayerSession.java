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
import android.media.MediaPlayer;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.util.Map;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.client.ClientUtils;
import ie.macinnes.tvheadend.model.Channel;

public class MediaPlayerSession extends BaseSession {
    private static final String TAG = MediaPlayerSession.class.getName();

    private MediaPlayer mMediaPlayer;

    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public MediaPlayerSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurface (" + mSessionNumber + ")");

        mSurface = surface;

        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(surface);
        }

        return true;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");

        mVolume = volume;

        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(volume, volume);
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
        Uri videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/stream/channel/" + channelUuid + "?profile=tif");

        // Prepare the media player
        mMediaPlayer = prepareMediaPlayer(videoUri, headers);

        // Start the media playback
        Log.d(TAG, "Starting playback of channel: " + channel.toString());
        mMediaPlayer.start();
        notifyVideoAvailable();

        return mMediaPlayer != null;
    }

    protected void stopPlayback() {
        Log.d(TAG, "Session stopPlayback (" + mSessionNumber + ")");

        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(null);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private MediaPlayer prepareMediaPlayer(Uri videoUri, Map<String, String> headers) {
        // Create and prep the MediaPlayer instance
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

                boolean handled = false;

                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        Log.d(TAG, "Buffering Start");
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                        handled = true;
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        Log.d(TAG, "Buffering End");
                        notifyVideoAvailable();
                        handled = true;
                        break;
                    case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        Log.d(TAG, "Rendering Start");
                        notifyVideoAvailable();
                        handled = true;
                        break;
                    default:
                        Log.d(TAG, "Video info: " + what + ", Extra = " + extra);
                        break;
                }

                return handled;
            }
        });


        try {
            Log.d(TAG, "Preparing video: " + videoUri + ".");

            mediaPlayer.setDataSource(mContext, videoUri, headers);
            mediaPlayer.prepare();

        } catch (Throwable e) {
            Log.e(TAG, "Error preparing video: " + e);

            mediaPlayer.release();

            return null;
        }

        mediaPlayer.setSurface(mSurface);
        mediaPlayer.setVolume(mVolume, mVolume);

        return mediaPlayer;
    }
}
