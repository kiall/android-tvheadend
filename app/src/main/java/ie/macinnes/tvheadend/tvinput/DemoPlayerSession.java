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
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.client.ClientUtils;
import ie.macinnes.tvheadend.demoplayer.DemoPlayer;
import ie.macinnes.tvheadend.demoplayer.ExtractorWithHTTPHeadersRendererBuilder;
import ie.macinnes.tvheadend.model.Channel;

public class DemoPlayerSession extends BaseSession implements DemoPlayer.Listener {
    private static final String TAG = DemoPlayerSession.class.getName();

    private DemoPlayer mDemoPlayer;

    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public DemoPlayerSession(Context context, Handler serviceHandler) {
        super(context, serviceHandler);
        Log.d(TAG, "Session created (" + mSessionNumber + ")");
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurface (" + mSessionNumber + ")");

        mSurface = surface;

        if (mDemoPlayer != null) {
            mDemoPlayer.setSurface(surface);
        }

        return true;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");

        mVolume = volume;

        if (mDemoPlayer != null) {
            mDemoPlayer.setVolume(volume);
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
        String httpPath = accountManager.getUserData(account, Constants.KEY_HTTP_PATH);

        // Create authentication headers and streamUri
        Map<String, String> headers = ClientUtils.createBasicAuthHeader(username, password);
        Uri videoUri;

        if (httpPath == null) {
            videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/stream/channel/" + channelUuid + "?profile=tif");
        } else {
            videoUri = Uri.parse("http://" + hostname + ":" + httpPort + "/" + httpPath + "/stream/channel/" + channelUuid + "?profile=tif");
        }

        // Prepare the media player
        mDemoPlayer = prepareMediaPlayer(videoUri, headers);

        if (mDemoPlayer != null) {
            // Start the media playback
            Log.d(TAG, "Starting playback of channel: " + channel.toString());
            mDemoPlayer.setPlayWhenReady(true);

            return true;
        } else {
            Toast.makeText(mContext, "Failed to prepare video", Toast.LENGTH_SHORT).show();

            return false;
        }
    }

    protected void stopPlayback() {
        Log.d(TAG, "Session stopPlayback (" + mSessionNumber + ")");

        if (mDemoPlayer != null) {
            mDemoPlayer.removeListener(this);
            mDemoPlayer.setSurface(null);
            mDemoPlayer.stop();
            mDemoPlayer.release();
            mDemoPlayer = null;
        }
    }

    private DemoPlayer prepareMediaPlayer(Uri videoUri, Map<String, String> headers) {
        Log.d(TAG, "Preparing video: " + videoUri + ".");

        // Create and prep the DemoPlayer instance
        String userAgent = Util.getUserAgent(mContext, "android-tvheadend");

        DemoPlayer demoPlayer = new DemoPlayer(
                new ExtractorWithHTTPHeadersRendererBuilder(
                        mContext, userAgent, videoUri, headers));

        demoPlayer.addListener(this);
        demoPlayer.prepare();
        demoPlayer.setSurface(mSurface);
        demoPlayer.setVolume(mVolume);

        return demoPlayer;
    }

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {

            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);

        } else if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
            notifyTracksChanged(getAllTracks());

            String audioId = getTrackId(TvTrackInfo.TYPE_AUDIO,
                    mDemoPlayer.getSelectedTrack(TvTrackInfo.TYPE_AUDIO));
            String videoId = getTrackId(TvTrackInfo.TYPE_VIDEO,
                    mDemoPlayer.getSelectedTrack(TvTrackInfo.TYPE_VIDEO));
            String textId = getTrackId(TvTrackInfo.TYPE_SUBTITLE,
                    mDemoPlayer.getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE));

            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, audioId);
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, videoId);
            notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, textId);
            notifyVideoAvailable();
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.d(TAG, "onVideoSizeChanged W:" + width + " H:" + height);
    }

    private List<TvTrackInfo> getAllTracks() {
        String trackId;
        List<TvTrackInfo> tracks = new ArrayList<>();

        int[] trackTypes = {
                DemoPlayer.TYPE_AUDIO,
                DemoPlayer.TYPE_VIDEO,
                DemoPlayer.TYPE_TEXT
        };

        for (int trackType : trackTypes) {
            int count = mDemoPlayer.getTrackCount(trackType);
            for (int i = 0; i < count; i++) {
                MediaFormat format = mDemoPlayer.getTrackFormat(trackType, i);
                trackId = getTrackId(trackType, i);
                TvTrackInfo.Builder builder = new TvTrackInfo.Builder(trackType, trackId);

                if (trackType == DemoPlayer.TYPE_VIDEO) {
                    if (format.pixelWidthHeightRatio == format.NO_VALUE) {
                        builder.setVideoWidth(format.width);
                    } else {
                        builder.setVideoWidth(Math.round(format.width * format.pixelWidthHeightRatio));
                    }
                    builder.setVideoHeight(format.height);
                } else if (trackType == DemoPlayer.TYPE_AUDIO) {
                    builder.setAudioChannelCount(format.channelCount);
                    builder.setAudioSampleRate(format.sampleRate);
                    if (format.language != null) {
                        builder.setLanguage(format.language);
                    }
                } else if (trackType == DemoPlayer.TYPE_TEXT) {
                    if (format.language != null) {
                        builder.setLanguage(format.language);
                    }
                }

                tracks.add(builder.build());
            }
        }

        return tracks;
    }

    private static String getTrackId(int trackType, int trackIndex) {
        return trackType + "-" + trackIndex;
    }
}
