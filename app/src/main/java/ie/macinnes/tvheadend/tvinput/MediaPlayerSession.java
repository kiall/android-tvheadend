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
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import java.util.HashMap;
import java.util.Map;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.model.Channel;

public class MediaPlayerSession extends BaseSession {
    private static final String TAG = MediaPlayerSession.class.getName();

    private MediaPlayer mMediaPlayer;
    private PrepareVideoTask mPrepareVideoTask;

    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public MediaPlayerSession(Context context) {
        super(context);
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

    @Override
    public boolean onTune(Uri channelUri) {
        Log.d(TAG, "Session onTune: " + channelUri + " (" + mSessionNumber + ")");

        // Notify we are busy tuning
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

        // Stop any existing playback
        stopPlayback();

        // Prepare for a new playback
        mPrepareVideoTask = new PrepareVideoTask(mContext, channelUri, 30000) {
            @Override
            protected void onPostExecute(MediaPlayer mediaPlayer) {
                if (mediaPlayer != null) {
                    mMediaPlayer = mediaPlayer;

                    mediaPlayer.setSurface(mSurface);
                    mediaPlayer.setVolume(mVolume, mVolume);
                    mediaPlayer.start();

                    notifyVideoAvailable();
                } else {
                    Log.e(TAG, "Error preparing media playback");
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                }
            }
        };

        mPrepareVideoTask.execute();

        return true;
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        Log.d(TAG, "Session onSetCaptionEnabled: " + enabled + " (" + mSessionNumber + ")");
    }

    @Override
    public void onRelease() {
        Log.d(TAG, "Session onRelease (" + mSessionNumber + ")");
        stopPlayback();
    }

    private void stopPlayback() {
        Log.d(TAG, "Session stopPlayback (" + mSessionNumber + ")");
        if (mPrepareVideoTask != null) {
            mPrepareVideoTask.cancel(true);
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(null);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public class PrepareVideoTask extends AsyncTask<Void, Void, MediaPlayer> {
        public final String TAG = PrepareVideoTask.class.getSimpleName();

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
            Account account = AccountUtils.getActiveAccount(mContext);

            String username = account.name;
            String password = accountManager.getPassword(account);
            String hostname = accountManager.getUserData(account, Constants.KEY_HOSTNAME);
            String httpPort = accountManager.getUserData(account, Constants.KEY_HTTP_PORT);

            // Create authentication headers and streamUri
            Map<String, String> headers = createBasicAuthHeader(username, password);
            Uri videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/stream/channel/" + channelUuid + "?profile=tif");

            if (isCancelled()) {
                return null;
            }

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

            if (isCancelled()) {
                return null;
            }

            try {
                mediaPlayer.setDataSource(mContext, videoUri, headers);

                if (isCancelled()) {
                    return null;
                }

                Log.d(TAG, "Preparing video: " + videoUri + ".");
                mediaPlayer.prepareAsync();

                synchronized (prepareLock) {
                    prepareLock.wait(mTimeout);
                    if (!prepared) {
                        throw new InterruptedException("Video prepare timed out after " + mTimeout + " ms.");
                    }
                }

                if (isCancelled()) {
                    return null;
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
}
