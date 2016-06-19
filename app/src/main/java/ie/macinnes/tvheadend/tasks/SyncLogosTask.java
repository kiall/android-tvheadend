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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import ie.macinnes.tvheadend.client.TVHClient;

public class SyncLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
    public static final String TAG = SyncLogosTask.class.getSimpleName();

    private final Context mContext;
    private final TVHClient mClient;
    private final ContentResolver mContentResolver;

    public SyncLogosTask(Context context) {
        mContext = context;

        mClient = TVHClient.getInstance(context);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public Void doInBackground(Map<Uri, String>... logosList) {
        for (Map<Uri, String> logos : logosList) {
            for (Uri uri : logos.keySet()) {
                insertUrl(mContext, uri, logos.get(uri));
            }
        }
        return null;
    }

    private void insertUrl(Context context, Uri contentUri, String sourceUrl) {
        Log.d(TAG, "Inserting logo " + sourceUrl + " to " + contentUri);

        Bitmap logo;

        try {
            logo = mClient.getChannelIcon(sourceUrl);
        } catch (InterruptedException|ExecutionException|TimeoutException e) {
            Log.d(TAG, "Failed to fetch logo from " + sourceUrl, e);
            return;
        }

        OutputStream os = null;

        try {
            os = mContentResolver.openOutputStream(contentUri);
            logo.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to copy " + sourceUrl + "  to " + contentUri, ioe);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // Ignore...
                }
            }
        }
    }
}
