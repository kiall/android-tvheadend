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
package ie.macinnes.tvheadend.client;

import android.graphics.Bitmap;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;

import java.util.Map;


public class ImageRequest extends com.android.volley.toolbox.ImageRequest {
    private static final String TAG = ImageRequest.class.getName();

    private String mUsername;
    private String mPassword;

    public ImageRequest(String url, Response.Listener<Bitmap> listener, Response.ErrorListener errorListener, String username, String password) {
        super(url, listener, 0, 0, null, null, errorListener);

        mUsername = username;
        mPassword = password;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return ClientUtils.createBasicAuthHeader(mUsername, mPassword);
    }
}
