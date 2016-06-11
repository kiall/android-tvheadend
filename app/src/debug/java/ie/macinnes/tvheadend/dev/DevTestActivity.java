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
package ie.macinnes.tvheadend.dev;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONObject;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.setup.TvInputSetupActivity;
import ie.macinnes.tvheadend.sync.SyncUtils;

public class DevTestActivity extends Activity {
    private static final String TAG = DevTestActivity.class.getName();
    private static final String NEWLINE = System.getProperty("line.separator");

    private AccountManager mAccountManager;
    private TVHClient mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_test);

        mAccountManager = AccountManager.get(getBaseContext());
        mClient = TVHClient.getInstance(getBaseContext());

        setClickHandlers();
    }

    private void setClickHandlers() {
        findViewById(R.id.serverInfoButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRunning();
                serverInfo();
            }
        });

        findViewById(R.id.accountInfoButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRunning();
                accountInfo();
            }
        });

        findViewById(R.id.channelGridButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRunning();
                channelGrid();
            }
        });

        findViewById(R.id.eventGridButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRunning();
                eventGrid();
            }
        });
    }

    private void setRunning() {
        TextView v = (TextView) findViewById(R.id.statusOutput);
        v.setText("RUNNING");
        clearDebugOutput();    }

    private void setOk() {
        TextView v = (TextView) findViewById(R.id.statusOutput);
        v.setText("OK");
    }

    private void setFail() {
        TextView v = (TextView) findViewById(R.id.statusOutput);
        v.setText("FAIL");
    }

    private void clearDebugOutput() {
        TextView v = (TextView) findViewById(R.id.debugOutput);
        v.setText(null);
    }

    private void setDebugOutput(String string) {
        TextView v = (TextView) findViewById(R.id.debugOutput);
        v.setText(string);
    }

    private void appendDebugOutput(String string) {
        TextView v = (TextView) findViewById(R.id.debugOutput);
        v.append(string + NEWLINE);
    }

    public void accountInfo() {
        Account[] accounts = mAccountManager.getAccountsByType("ie.macinnes.tvheadend");

        appendDebugOutput("Number of Accounts: " + Integer.toString(accounts.length));

        for (Account account : accounts) {
            appendDebugOutput("---");
            appendDebugOutput("Account toString: " + account.toString());

            String username = account.name;
            appendDebugOutput("Account UserName: " + username);

            String password = mAccountManager.getPassword(account);
            appendDebugOutput("Account Password: " + password);

            String hostname = mAccountManager.getUserData(account, Constants.KEY_HOSTNAME);
            appendDebugOutput("Account Hostname: " + hostname);

            String port = mAccountManager.getUserData(account, Constants.KEY_PORT);
            appendDebugOutput("Account Port: " + port);

            mClient.setConnectionInfo(hostname, port, username, password);
        }

        setOk();
    }

    public void serverInfo() {
        Response.Listener<JSONObject> listener = new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                setOk();
                setDebugOutput(response.toString());
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                setFail();
            }
        };

        mClient.getServerInfo(listener, errorListener);
    }

    public void channelGrid() {
        Response.Listener<TVHClient.ChannelList> listener = new Response.Listener<TVHClient.ChannelList>() {

            @Override
            public void onResponse(TVHClient.ChannelList response) {
                for (TVHClient.Channel chan : response.entries ) {
                    appendDebugOutput("Name: " + chan.name);
                }
                setOk();
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                setFail();
            }
        };

        mClient.getChannelGrid(listener, errorListener);
    }

    public void eventGrid() {
        Response.Listener<TVHClient.EventList> listener = new Response.Listener<TVHClient.EventList>() {

            @Override
            public void onResponse(TVHClient.EventList response) {
                for (TVHClient.Event event : response.entries ) {
                    appendDebugOutput("Title: " + event.title);
                }
                setOk();
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                setFail();
            }
        };

//        mClient.getEventGrid(listener, errorListener);
    }

    public void rescanChannels(View view) {
        Intent intent = new Intent(this, TvInputSetupActivity.class);
        intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, "ie.macinnes.tvheadend/.TvheadendTvInputService");
        startActivity(intent);
    }

    public void syncEpg(View view) {
        SyncUtils.requestSync(getBaseContext(), "ie.macinnes.tvheadend/.TvheadendTvInputService");
    }

}
