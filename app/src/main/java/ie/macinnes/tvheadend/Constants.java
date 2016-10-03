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

    // Misc Things
    public static final String CONTENT_AUTHORITY = TvContract.AUTHORITY;
    public static final String ACCOUNT_TYPE = "ie.macinnes.tvheadend";

    // Sync Things
    public static final String SYNC_EXTRAS_QUICK = "QUICK";

    // Preferences Files and Keys
    public static final String PREFERENCE_TVHEADEND = "tvheadend";

    // Session Selection Preference Keys and Values
    public static final String KEY_SESSION = "SESSION";
    public static final String SESSION_MEDIA_PLAYER = "SESSION-MEDIA-PLAYER";
    public static final String SESSION_EXO_PLAYER = "SESSION-EXO-PLAYER";
    public static final String SESSION_VLC = "SESSION-VLC";

    // Bundle and Preference Keys
    public static final String KEY_APP_VERSION = "APP-VERSION";
    public static final String KEY_HOSTNAME = "HOSTNAME";
    public static final String KEY_HTTP_PORT = "HTTP-PORT";
    public static final String KEY_HTTP_PATH = "HTTP-PATH";
    public static final String KEY_USERNAME = "USERNAME";
    public static final String KEY_PASSWORD = "PASSWORD";
    public static final String KEY_ERROR_MESSAGE = "ERROR-MESSAGE";

    // Deinterlace Preferences Keys and Values
    public static final String KEY_DEINTERLACE_ENABLED = "DEINTERLACE-ENABLED";
    public static final String KEY_DEINTERLACE_METHOD = "DEINTERLACE-METHOD";
    public static final String DEINTERLACE_BLEND = "blend";
    public static final String DEINTERLACE_MEAN = "mean";
    public static final String DEINTERLACE_BOB = "bob";
    public static final String DEINTERLACE_LINEAR = "linear";
    public static final String DEINTERLACE_YADIF = "yadif";
    public static final String DEINTERLACE_YADIF2X = "yadif2x";
    public static final String DEINTERLACE_PHOSPHOR = "phosphor";
    public static final String DEINTERLACE_IVTC = "ivtc";

    // Scaling Preferences Keys and Values
    public static final String KEY_SCALING_ENABLED = "SCALING-ENABLED";
    public static final String KEY_SCALING_METHOD = "SCALING-METHOD";
    public static final int SCALING_FAST_BILINEAR = 0;
    public static final int SCALING_BILINEAR = 1;
    public static final int SCALING_BICUBIC = 2;
    public static final int SCALING_AREA = 5;
    public static final int SCALING_LUMA_BICUBIC = 6;
    public static final int SCALING_GAUSS = 7;
    public static final int SCALING_SINCR = 8;
    public static final int SCALING_LANCZOS = 9;
    public static final int SCALING_BICUBIC_SPLINE = 10;

    // Hardware Acceleration Preferences Keys and Values
    public static final String KEY_HW_ACCEL_METHOD = "HW-ACCEL-METHOD";
    public static final int HW_ACCEL_AUTOMATIC = 1;
    public static final int HW_ACCEL_ENABLED = 2;
    public static final int HW_ACCEL_DISABLED = 3;

    // Network Buffering Preferences Keys and Values
    public static final String KEY_NETWORK_BUFFER = "NETWORK-BUFFER";
}
