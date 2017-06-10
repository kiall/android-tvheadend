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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.acra.ACRA;

import ie.macinnes.tvheadend.account.AccountUtils;
import ie.macinnes.tvheadend.settings.SettingsActivity;
import ie.macinnes.tvheadend.sync.EpgSyncService;
import ie.macinnes.tvheadend.tvinput.TvInputService;

public class DevTestActivity extends Activity {
    private static final String TAG = DevTestActivity.class.getName();
    private static final String NEWLINE = System.getProperty("line.separator");

    private AccountManager mAccountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dev_test);

        mAccountManager = AccountManager.get(getBaseContext());
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
        }

        setOk();
    }

    public void deleteChannels(View view) {
        setRunning();

        Context context = getBaseContext();
        Intent i = new Intent(context, EpgSyncService.class);
        context.stopService(i);

        i = new Intent(context, TvInputService.class);
        context.stopService(i);

        TvContractUtils.removeChannels(getBaseContext());
        setOk();
    }

    public void deleteRecordedPrograms(View view) {
        setRunning();

        Context context = getBaseContext();
        Intent i = new Intent(context, EpgSyncService.class);
        context.stopService(i);

        i = new Intent(context, TvInputService.class);
        context.stopService(i);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TvContractUtils.removeRecordedProgram(getBaseContext());
        }
        setOk();
    }

    public void showPreferences(View view) {
        startActivity(SettingsActivity.getPreferencesIntent(this));
    }

    public void restartEpgSyncService(View view) {
        Context context = getBaseContext();
        Intent i = new Intent(context, EpgSyncService.class);
        context.stopService(i);
        context.startService(i);
    }

    public void restartTvInputService(View view) {
        Context context = getBaseContext();
        Intent i = new Intent(context, TvInputService.class);
        context.stopService(i);
        context.startService(i);
    }

    public void sendCrashReport(View view) {
        Exception e = new Exception("Test Crash Report");
        ACRA.getErrorReporter().handleException(e);
    }
}
