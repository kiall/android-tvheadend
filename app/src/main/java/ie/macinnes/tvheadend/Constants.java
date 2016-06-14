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


public class Constants {

    // Preference Files
    public static final String PREFERENCE_TVHEADEND = "tvheadend";

    // Bundle Keys
    public static final String KEY_HOSTNAME = "HOSTNAME";
    public static final String KEY_PORT = "PORT";
    public static final String KEY_USERNAME = "USERNAME";
    public static final String KEY_PASSWORD = "PASSWORD";

    public static final String KEY_INPUT_ID = "INPUT-ID";

    public static final String KEY_ERROR_MESSAGE = "ERROR-MESSAGE";

    // Sync Job IDs
    public static final int PERIODIC_SYNC_JOB_ID = 0;
    public static final int REQUEST_SYNC_JOB_ID = 1;

    // Sync Status
    public static final String ACTION_SYNC_STATUS_CHANGED = "action_sync_status_changed";
    public static final String SYNC_STATUS = "sync_status";
    public static final String SYNC_FINISHED = "sync_finished";
    public static final String SYNC_STARTED = "sync_started";
}
