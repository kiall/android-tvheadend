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

package ie.macinnes.htsp.tasks;

import android.os.Handler;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;

import ie.macinnes.htsp.Connection;
import ie.macinnes.htsp.IMessageListener;
import ie.macinnes.htsp.MessageListener;
import ie.macinnes.htsp.ResponseMessage;
import ie.macinnes.htsp.messages.AuthenticateRequest;
import ie.macinnes.htsp.messages.AuthenticateResponse;
import ie.macinnes.htsp.messages.HelloRequest;
import ie.macinnes.htsp.messages.HelloResponse;

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
        mConnection.sendMessage(helloRequest);
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
        mConnection.sendMessage(authenticateRequest);
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
