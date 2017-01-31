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

package ie.macinnes.htspold.tasks;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import ie.macinnes.htspold.ConnectionException;
import ie.macinnes.htspold.MessageListener;
import ie.macinnes.htspold.ResponseMessage;
import ie.macinnes.htspold.messages.AuthenticateRequest;
import ie.macinnes.htspold.messages.AuthenticateResponse;
import ie.macinnes.htspold.messages.HelloRequest;
import ie.macinnes.htspold.messages.HelloResponse;

public class AuthenticateTask extends MessageListener {
    private static final String TAG = AuthenticateTask.class.getName();

    private String mUsername;
    private String mPassword;
    private String mClientName;
    private String mClientVersion;

    private IAuthenticateTaskCallback mCallback;

    public AuthenticateTask(String username, String password, String clientName, String clientVersion) {
        mUsername = username;
        mPassword = password;
        mClientName = clientName;
        mClientVersion = clientVersion;
    }

    @Override
    public void onMessage(ResponseMessage message) {
        Log.v(TAG, "Received Message: " + message.getClass() + " / " + message.toString());

        if (message.getClass() == HelloResponse.class) {
            sendAuthenticate((HelloResponse) message);
        } else if (message.getClass() == AuthenticateResponse.class) {
            completeAuthentication((AuthenticateResponse) message);
        }
    }

    public void authenticate(IAuthenticateTaskCallback callback) {
        mCallback = callback;

        sendHello();
    }

    private void sendHello() {
        Log.d(TAG, "Prepping helloRequest");
        HelloRequest helloRequest = new HelloRequest();

        helloRequest.setHtspVersion(23);
        helloRequest.setClientName(mClientName);
        helloRequest.setClientVersion(mClientVersion);
        helloRequest.setUsername(mUsername);

        Log.d(TAG, "Sending helloRequest");
        try {
            mConnection.sendMessage(helloRequest);
        } catch (ConnectionException e) {
            Log.w(TAG, "Failed to send helloRequest", e);
            mCallback.onFailure();
            return;
        }
    }

    private void sendAuthenticate(HelloResponse response) {
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("DERP. Your platform doesn't support SHA-1");
        }

        try {
            md.update(mPassword.getBytes("utf8"));
            md.update(response.getChallenge());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("DERP. Your platform doesn't support UTF-8");
        }

        Log.d(TAG, "Prepping authenticateRequest");
        AuthenticateRequest authenticateRequest = new AuthenticateRequest();

        authenticateRequest.setUsername(mUsername);
        authenticateRequest.setDigest(md.digest());

        Log.d(TAG, "Sending authenticateRequest");
        try {
            mConnection.sendMessage(authenticateRequest);
        } catch (ConnectionException e) {
            Log.w(TAG, "Failed to send authenticateRequest", e);
            mCallback.onFailure();
            return;
        }
    }

    private void completeAuthentication(AuthenticateResponse response) {
        if (response.getError() != null || response.getNoAccess() == true) {
            mCallback.onFailure();
        } else {
            mCallback.onSuccess();
        }
    }

    public interface IAuthenticateTaskCallback {
        void onSuccess();
        void onFailure();
    }
}
