/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
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

package ie.macinnes.tvheadend.player.reader;

import android.content.Context;

public class StreamReadersFactory {
    private final Context mContext;

    public StreamReadersFactory(Context context) {
        mContext = context;
    }

    public StreamReader createStreamReader(String streamType) {
        switch (streamType) {
            // Video Stream Types
            case "H264":
                return new H264StreamReader(mContext);
            case "HEVC":
                return new H265StreamReader(mContext);
            case "MPEG2VIDEO":
                return new Mpeg2VideoStreamReader(mContext);
            // Audio Stream Types
            case "AAC":
                return new AacStreamReader(mContext);
            case "AC3":
                return new Ac3StreamReader(mContext);
            case "EAC3":
                return new Eac3StreamReader(mContext);
            case "MPEG2AUDIO":
                return new Mpeg2AudioStreamReader(mContext);
            case "VORBIS":
                return new VorbisStreamReader(mContext);
            // Text Stream Types
            case "TEXTSUB":
                return new TextsubStreamReader(mContext);
            default:
                return null;
        }
    }
}
