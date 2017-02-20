/*
 * Copyright (c) 2016 Alexander Pipelka
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
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

public class ShieldVideoRenderer extends MediaCodecVideoRenderer {
    private static final String TAG = ShieldVideoRenderer.class.getName();

    private long startTime;
    private boolean enabled;

    public ShieldVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, Handler eventHandler, VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @Override
    protected void configureCodec(
            MediaCodecInfo codecInfo, MediaCodec codec, Format format, MediaCrypto crypto) throws MediaCodecUtil.DecoderQueryException {
        super.configureCodec(codecInfo, codec, format, crypto);

        enabled = (format.height == 1080 || format.height == 576);
        startTime = System.currentTimeMillis();
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        long currentTime = System.currentTimeMillis();
        long diffMs = currentTime - startTime;

        if(enabled && diffMs > 13 * 60 * 1000) {
            Log.d(TAG, "Resetting codecs as nVidia Shield workaround");
            releaseCodec();
        }

        super.render(positionUs, elapsedRealtimeUs);
    }
}
