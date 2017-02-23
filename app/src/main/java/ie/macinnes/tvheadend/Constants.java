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
    public static final int MIGRATE_VERSION = 79;

    // Preferences Files and Keys
    public static final String PREFERENCE_TVHEADEND = "tvheadend";

    // Misc Preference Keys and Values
    public static final String KEY_SETUP_COMPLETE = "SETUP-COMPLETE";
    public static final String KEY_HTSP_VIDEO_ENABLED = "htsp_video_enabled";
    public static final String KEY_HTSP_STREAM_PROFILE = "htsp_stream_profile";
    public static final String KEY_HTTP_STREAM_PROFILE = "http_stream_profile";

    // Session Selection Preference Keys and Values
    public static final String KEY_SESSION = "SESSION";
    public static final String SESSION_MEDIA_PLAYER = "SESSION-MEDIA-PLAYER";
    public static final String SESSION_EXO_PLAYER = "SESSION-EXO-PLAYER";
    public static final String SESSION_VLC = "SESSION-VLC";

    // Bundle and Preference Keys
    public static final String KEY_APP_VERSION = "APP-VERSION";
    public static final String KEY_HOSTNAME = "HOSTNAME";
    public static final String KEY_HTSP_PORT = "HTSP-PORT";
    public static final String KEY_HTTP_PORT = "HTTP-PORT";
    public static final String KEY_HTTP_PATH = "HTTP-PATH";
    public static final String KEY_USERNAME = "USERNAME";
    public static final String KEY_PASSWORD = "PASSWORD";
    public static final String KEY_ERROR_MESSAGE = "ERROR-MESSAGE";

    // VLC Preference Keys
    public static final String KEY_DEINTERLACE_ENABLED = "vlc_deinterlace_enabled";
    public static final String KEY_DEINTERLACE_METHOD = "vlc_deinterlace_method";

    // Session Selection Preference Keys and Values
    public static final String KEY_EPG_SYNC_ENABLED = "epg_sync_enabled";
    public static final String KEY_EPG_MAX_TIME = "epg_max_time";
    public static final String KEY_EPG_LAST_UPDATE_ENABLED = "epg_last_update_enabled";
    public static final String KEY_EPG_LAST_UPDATE = "EPG_LAST_UPDATE"; // Todo: This name is confusing...
    public static final String KEY_EPG_DEFAULT_POSTER_ART_ENABLED = "epg_default_poster_art_enabled";
}
