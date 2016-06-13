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
package ie.macinnes.tvheadend;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import ie.macinnes.tvheadend.tasks.PrepareVideoTask;


public class TvheadendTvInputService extends TvInputService {
    private static final String TAG = TvheadendTvInputService.class.getName();

    private final Object mPrepareLock = new Object();

    public TvheadendTvInputService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.d(TAG, "Creating new TvInputService Session for input ID: " + inputId + ".");

        return new TvheadendTvInputSessionImpl(this, inputId);
    }

    class TvheadendTvInputSessionImpl extends TvInputService.Session {
        private final Context mContext;
        private final String mInputId;
        private final TvInputManager mTvInputManager;

        private MediaPlayer mMediaPlayer;
        private Surface mSurface;

        /**
         * Creates a new Session.
         *
         * @param context The context of the application
         */
        public TvheadendTvInputSessionImpl(Context context, String inputId) {
            super(context);
            Log.d(TAG, "Session created");

            mContext = context;
            mInputId = inputId;
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        }

        @Override
        public void onRelease() {
            Log.d(TAG, "Session onRelease");
            stopPlayback();
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            Log.d(TAG, "Session onSetSurface");

            mSurface = surface;

            if (mMediaPlayer != null) {
                mMediaPlayer.setSurface(surface);
            }

            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
            Log.d(TAG, "Session onSetStreamVolume: " + volume);
            if (mMediaPlayer != null) {
                mMediaPlayer.setVolume(volume, volume);
            }
        }

        @Override
        public boolean onTune(Uri channelUri) {
            Log.d(TAG, "Session onTune: " + channelUri);

            // Notify we are busy tuning
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

            // Stop any existing playback
            stopPlayback();

            // Prepare for a new playback
            PrepareVideoTask prepareVideoTask = new PrepareVideoTask(getBaseContext(), channelUri, 30000) {
                @Override
                protected void onPostExecute(MediaPlayer mediaPlayer) {
                    mMediaPlayer = mediaPlayer;

                    if (mediaPlayer != null) {
                        mediaPlayer.setSurface(mSurface);
                        mediaPlayer.start();

                        notifyVideoAvailable();
                    } else {
                        Log.e(TAG, "Error preparing media playback");
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                    }
                }
            };

            prepareVideoTask.execute();

            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            Log.d(TAG, "Session onSetCaptionEnabled: " + enabled);
        }

        /**
         * Stop media playback
         */
        private void stopPlayback() {
            Log.d(TAG, "Session stopPlayback");
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }

    }
}
