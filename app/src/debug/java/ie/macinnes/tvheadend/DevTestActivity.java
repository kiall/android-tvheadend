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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONObject;

import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.migrate.MigrateUtils;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.model.Program;
import ie.macinnes.tvheadend.model.ProgramList;
import ie.macinnes.tvheadend.settings.SettingsActivity;

public class DevTestActivity extends Activity {
    private static final String TAG = DevTestActivity.class.getName();
    private static final String NEWLINE = System.getProperty("line.separator");

    private AccountManager mAccountManager;
    private TVHClient mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: Find a better (+ out of UI thread) way to do this.
        MigrateUtils.doMigrate(getBaseContext());

        setContentView(R.layout.activity_dev_test);

        mAccountManager = AccountManager.get(getBaseContext());
        mClient = TVHClient.getInstance(getBaseContext());
    }

    private void setRunning() {
        TextView v = (TextView) findViewById(R.id.statusOutput);
        v.setText("RUNNING");
        clearDebugOutput();
    }

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

    public void accountInfo(View view) {
        setRunning();

        Account[] accounts = AccountUtils.getAllAccounts(this);

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

            String htspPort = mAccountManager.getUserData(account, Constants.KEY_HTSP_PORT);
            appendDebugOutput("Account HTSP Port: " + htspPort);

            String httpPort = mAccountManager.getUserData(account, Constants.KEY_HTTP_PORT);
            appendDebugOutput("Account HTTP Port: " + httpPort);

            String httpPath = mAccountManager.getUserData(account, Constants.KEY_HTTP_PATH);
            appendDebugOutput("Account HTTP Path: " + httpPath);

            mClient.setConnectionInfo(hostname, httpPort, httpPath, username, password);
        }

        setOk();
    }

    public void serverInfo(View view) {
        setRunning();

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

    public void channelList(View view) {
        setRunning();
        ChannelList channels = TvContractUtils.getChannels(getBaseContext(), null);
        for (Channel channel : channels) {
            appendDebugOutput(channel.toString());
        }
        setOk();
    }

    public void programList(View view) {
        setRunning();
        ChannelList channels = TvContractUtils.getChannels(getBaseContext(), null);
        for (Channel channel : channels) {
            appendDebugOutput(channel.toString());
            appendDebugOutput("---");

            ProgramList programs = TvContractUtils.getPrograms(getBaseContext(), channel);

            int i = 0;

            for (Program program : programs) {
                appendDebugOutput(program.toString());
                i++;

                if (i == 5) {
                    break;
                }
            }
        }
        appendDebugOutput("---");
        setOk();
    }

    public void deleteChannels(View view) {
        setRunning();
        TvContractUtils.removeChannels(getBaseContext());
        setOk();
    }

    public void showPreferences(View view) {
        startActivity(SettingsActivity.getPreferencesIntent(this));
    }
}
