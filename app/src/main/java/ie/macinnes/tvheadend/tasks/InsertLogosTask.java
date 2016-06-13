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

package ie.macinnes.tvheadend.tasks;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import ie.macinnes.tvheadend.utils.TvContractUtils;

public class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
    public static final String TAG = InsertLogosTask.class.getSimpleName();

    private final Context mContext;

    public InsertLogosTask(Context context) {
        mContext = context;

    }

    @Override
    public Void doInBackground(Map<Uri, String>... logosList) {
        for (Map<Uri, String> logos : logosList) {
            for (Uri uri : logos.keySet()) {
                try {
                    insertUrl(mContext, uri, new URL(logos.get(uri)));
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Failed to load icon " + logos.get(uri), e);
                }
            }
        }
        return null;
    }

    private void insertUrl(Context context, Uri contentUri, URL sourceUrl) {
        Log.d(TAG, "Inserting logo " + sourceUrl + " to " + contentUri);

        // TODO: Support authentication when fetching logs
        InputStream is = null;
        OutputStream os = null;

        try {
            is = sourceUrl.openStream();
            os = context.getContentResolver().openOutputStream(contentUri);
            copy(is, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to copy " + sourceUrl + "  to " + contentUri, ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore...
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // Ignore...
                }
            }
        }
    }

    private void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }
}
