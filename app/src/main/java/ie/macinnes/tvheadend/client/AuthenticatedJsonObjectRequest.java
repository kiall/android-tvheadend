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
package ie.macinnes.tvheadend.client;

import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AuthenticatedJsonObjectRequest extends JsonObjectRequest {
    private static final String TAG = AuthenticatedJsonObjectRequest.class.getName();

    private String mUsername;
    private String mPassword;
    private Map<String, String> mParams;

    public AuthenticatedJsonObjectRequest(
            int method, String url, JSONObject jsonRequest, Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener, String username, String password) {
        super(method, url, jsonRequest, listener, errorListener);

        mUsername = username;
        mPassword = password;
    }

    public AuthenticatedJsonObjectRequest(
            int method, String url, JSONObject jsonRequest, Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener, String username, String password, Map<String, String> params) {
        super(method, url, jsonRequest, listener, errorListener);

        mUsername = username;
        mPassword = password;
        mParams = params;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return createBasicAuthHeader(mUsername, mPassword);
    }

    public Map<String, String> getParams() {
        return mParams;
    }

    private Map<String, String> createBasicAuthHeader(String username, String password) {
        Map<String, String> headerMap = new HashMap<String, String>();

        String credentials = username + ":" + password;
        String base64EncodedCredentials =
                Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        headerMap.put("Authorization", "Basic " + base64EncodedCredentials);

        return headerMap;
    }
}
