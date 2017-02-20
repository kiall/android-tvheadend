/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ie.macinnes.tvheadend.player;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;


public class SimpleTvheadendPlayer extends SimpleExoPlayer {
    private static final String TAG = SimpleTvheadendPlayer.class.getName();

    public SimpleTvheadendPlayer(Context context, TrackSelector trackSelector, LoadControl loadControl,
                                 DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode,
                                 long allowedVideoJoiningTimeMs) {
        super(context, trackSelector, loadControl, drmSessionManager, extensionRendererMode, allowedVideoJoiningTimeMs);
    }

    @Override
    protected void buildAudioRenderers(Context context, Handler mainHandler, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                       int extensionRendererMode, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        AudioCapabilities audioCapabilities = AudioCapabilities.getCapabilities(context);

        // FFMpeg Audio Decoder
        Log.d(TAG, "Adding FfmpegAudioRenderer");
        out.add(new FfmpegAudioRenderer(mainHandler, eventListener, audioCapabilities));

        // Native Audio Decoders
        Log.d(TAG, "Adding MediaCodecAudioRenderer");
        out.add(new MediaCodecAudioRenderer(MediaCodecSelector.DEFAULT, drmSessionManager,
                true, mainHandler, eventListener, audioCapabilities));
    }

    @Override
    protected void buildVideoRenderers(Context context, Handler mainHandler, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                       int extensionRendererMode, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs,
                                       ArrayList<Renderer> out) {
        if (Build.MODEL.equals("SHIELD Android TV")) {
            Log.d(TAG, "Adding ShieldVideoRenderer");
            out.add(new ShieldVideoRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    allowedVideoJoiningTimeMs,
                    drmSessionManager,
                    false,
                    mainHandler,
                    eventListener,
                    SimpleExoPlayer.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
        } else {
            Log.d(TAG, "Adding MediaCodecVideoRenderer");
            out.add(new MediaCodecVideoRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    allowedVideoJoiningTimeMs,
                    drmSessionManager,
                    false,
                    mainHandler,
                    eventListener,
                    SimpleExoPlayer.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
        }
    }
}
