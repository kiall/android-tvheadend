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


import android.media.tv.TvContract;

public class Constants {
    // Use sparingly, i.e. for wrapping log messages that would otherwise spam during normal
    // operation. Even Log.v/Log.v logs will slow things down if called enough.
    public static final boolean DEBUG = false;

    // Misc Things
    public static final String CONTENT_AUTHORITY = TvContract.AUTHORITY;
    public static final String ACCOUNT_TYPE = "ie.macinnes.tvheadend";
    public static final int MIGRATE_VERSION = 81;

    // Preferences Files and Keys
    public static final String PREFERENCE_TVHEADEND = "tvheadend";

    // Misc Preference Keys and Values
    public static final String KEY_SETUP_COMPLETE = "SETUP-COMPLETE";

    // Audio and Video Preferences Keys and Values
    public static final String KEY_BUFFER_PLAYBACK_MS = "buffer_playback_ms";
    public static final String KEY_AUDIO_PASSTHROUGH_DECODER_ENABLED = "audio_passthrough_decodeder_enabled";
    public static final String KEY_FFMPEG_AUDIO_ENABLED = "ffmpeg_audio_enabled";
    public static final String KEY_CAPTIONS_APPLY_EMBEDDED_STYLES = "captions_apply_embedded_styles";

    // Advanced Preferences Keys and Values
    public static final String KEY_SHIELD_WORKAROUND_ENABLED = "shield_workaround_enabled";
    public static final String KEY_DEBUG_TEXT_VIEW_ENABLED = "debug_text_view_enabled";
    public static final String KEY_TIMESHIFT_ENABLED = "timeshift_enabled";
    public static final String KEY_HTSP_STREAM_PROFILE = "htsp_stream_profile";

    // Bundle and Preference Keys
    public static final String KEY_APP_VERSION = "APP-VERSION";
    public static final String KEY_HOSTNAME = "HOSTNAME";
    public static final String KEY_HTSP_PORT = "HTSP-PORT";
    public static final String KEY_USERNAME = "USERNAME";
    public static final String KEY_PASSWORD = "PASSWORD";
    public static final String KEY_ERROR_MESSAGE = "ERROR-MESSAGE";

    // Session Selection Preference Keys and Values
    public static final String KEY_EPG_SYNC_ENABLED = "epg_sync_enabled";
    public static final String KEY_EPG_MAX_TIME = "epg_max_time";
    public static final String KEY_EPG_LAST_UPDATE_ENABLED = "epg_last_update_enabled";
    public static final String KEY_EPG_LAST_UPDATE = "EPG_LAST_UPDATE"; // Todo: This name is confusing...
    public static final String KEY_EPG_DEFAULT_POSTER_ART_ENABLED = "epg_default_poster_art_enabled";
}
