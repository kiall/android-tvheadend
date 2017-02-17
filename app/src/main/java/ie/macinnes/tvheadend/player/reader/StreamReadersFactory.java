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

public class StreamReadersFactory {
    public StreamReader createStreamReader(String streamType) {
        switch (streamType) {
            // Video Stream Types
            case "H264":
                return new H264StreamReader();
            case "HEVC":
                return new H265StreamReader();
            case "MPEG2VIDEO":
                return new Mpeg2VideoStreamReader();
            // Audio Stream Types
            case "AAC":
                return new AacStreamReader();
            case "AC3":
                return new Ac3StreamReader();
            case "EAC3":
                return new Eac3StreamReader();
            case "MPEG2AUDIO":
                return new Mpeg2AudioStreamReader();
            case "VORBIS":
                return new VorbisStreamReader();
            // Text Stream Types
            case "TEXTSUB":
                return new TextsubStreamReader();
            default:
                return null;
        }
    }
}
