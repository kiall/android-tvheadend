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
package ie.macinnes.tvheadend.account;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import org.json.JSONObject;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.client.TVHClient;

public class AuthenticatorActivity extends AccountAuthenticatorActivity {
    private static final String TAG = TVHClient.class.getName();

    private AccountManager mAccountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_authenticator);

        mAccountManager = AccountManager.get(getBaseContext());

        findViewById(R.id.submit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
    }

    public void submit() {
        final String accountType = getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);

        final String accountName = ((TextView) findViewById(R.id.accountName)).getText().toString();
        final String accountPassword = ((TextView) findViewById(R.id.accountPassword)).getText().toString();
        final String accountHostname = ((TextView) findViewById(R.id.accountHostname)).getText().toString();
        final String accountPort = ((TextView) findViewById(R.id.accountPort)).getText().toString();

        // Validate the User and Pass by connecting to TVHeadend
        Response.Listener<JSONObject> listener = new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                Log.d(TAG, "Successfully validated credentials");

                Bundle data = new Bundle();

                data.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
                data.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
                data.putString(AccountManager.KEY_PASSWORD, accountPassword);
                data.putString(Constants.KEY_HOSTNAME, accountHostname);
                data.putString(Constants.KEY_PORT, accountPort);

                final Intent res = new Intent();
                res.putExtras(data);
                finishLogin(res);
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Failed to validate credentials");

                if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                    onError("Network Timeout!");
                } else if (error instanceof AuthFailureError) {
                    onError("Auth Failure!");
                } else if (error instanceof ServerError) {
                    onError("Unknown Server Error");
                } else if (error instanceof NetworkError) {
                    onError("Unknown Network Error");
                } else if (error instanceof ParseError) {
                    onError("Unknown Parse Error");
                } else {
                    onError("Unknown Error");
                }
            }
        };

        TVHClient client = TVHClient.getInstance(getBaseContext());

        client.setConnectionInfo(accountHostname, accountPort, accountName, accountPassword);

        client.getServerInfo(listener, errorListener);
    }

    private void finishLogin(Intent intent) {
        Log.d(TAG, "Storing new account");
        String accountType = intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accountPassword = intent.getStringExtra(AccountManager.KEY_PASSWORD);
        String accountHostname = intent.getStringExtra(Constants.KEY_HOSTNAME);
        String accountPort = intent.getStringExtra(Constants.KEY_PORT);

        final Account account = new Account(accountName, accountType);

        Bundle userdata = new Bundle();
        userdata.putString(Constants.KEY_HOSTNAME, accountHostname);
        userdata.putString(Constants.KEY_PORT, accountPort);

        mAccountManager.addAccountExplicitly(account, accountPassword, userdata);

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void onError(int errorResId) {
        Toast.makeText(getBaseContext(), errorResId, Toast.LENGTH_LONG).show();
    }

    private void onError(String errorMessage) {
        Toast.makeText(getBaseContext(), errorMessage, Toast.LENGTH_LONG).show();
    }
}
