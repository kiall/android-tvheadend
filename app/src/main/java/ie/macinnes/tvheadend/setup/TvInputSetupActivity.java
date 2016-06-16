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
package ie.macinnes.tvheadend.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.sync.SyncUtils;
import ie.macinnes.tvheadend.tasks.SyncChannelsTask;
import ie.macinnes.tvheadend.TvContractUtils;

public class TvInputSetupActivity extends Activity {
    private static final String TAG = TvInputSetupActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GuidedStepFragment fragment = new IntroFragment();
        fragment.setArguments(getIntent().getExtras());
        GuidedStepFragment.addAsRoot(this, fragment, android.R.id.content);
    }

    public static abstract class BaseGuidedStepFragment extends GuidedStepFragment {
        protected AccountManager mAccountManager;

        protected static Account sAccount;
        protected static TVHClient sClient;

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            String inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);

            if (inputId != null && inputId != TvContractUtils.getInputId()) {
                // Ensure the provided ID matches what we expect, as we only have a single input.
                throw new RuntimeException(
                        "Setup Activity called for unknown inputId: " + inputId);
            }

            mAccountManager = AccountManager.get(getActivity());
            sClient = TVHClient.getInstance(getActivity());
        }

        protected Account getAccountByName(String name) {
            Log.d(TAG, "getAccountByName(" + name + ")");

            Account[] accounts = mAccountManager.getAccountsByType(Constants.ACCOUNT_TYPE);

            Log.d(TAG, "Checking " + Integer.toString(accounts.length) + " accounts");

            for (Account account : accounts) {
                Log.d(TAG, "Checking Account: " + account.name);

                if (account.name.equals(name)) {
                    Log.d(TAG, "Found account");
                    return account;
                }
            }

            Log.d(TAG, "Failed to find account, no accounts with matching name");
            return null;
        }
    }

    public static class IntroFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Introduction",
                    "Welcome to the Tvhheadend Live Channel, we'll guide you through the setup " +
                    "process now, once done you will be ready to watch TV",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            List<GuidedAction> subActions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .title("Begin")
                    .description("Start Tvheadend Live Channel Setup")
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            // Move onto the next step
            GuidedStepFragment fragment = new AccountSelectorFragment();
            fragment.setArguments(getArguments());
            add(getFragmentManager(), fragment);
        }
    }

    public static class AccountSelectorFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_CONFIRM = 1;
        private static final int ACTION_ID_SELECT_ACCOUNT = 2;
        private static final int ACTION_ID_NEW_ACCOUNT = 3;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Select An Account",
                    "Please choose an existing, or create a new TVHeadend account to use",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            List<GuidedAction> subActions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SELECT_ACCOUNT)
                    .title("Account Selection")
                    .editTitle("")
                    .description("Select Account")
                    .subActions(subActions)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_CONFIRM)
                    .title("Confirm")
                    .description("Confirm This Selection")
                    .editable(false)
                    .build();
            action.setEnabled(false);

            actions.add(action);
        }

        @Override
        public void onResume() {
            super.onResume();
            Log.d(TAG, "onResume()");
            GuidedAction accountAction = findActionById(ACTION_ID_SELECT_ACCOUNT);

            List<GuidedAction> accountSubActions = accountAction.getSubActions();
            accountSubActions.clear();

            Account[] accounts = mAccountManager.getAccountsByType(Constants.ACCOUNT_TYPE);

            for (Account account : accounts) {
                GuidedAction action = new GuidedAction.Builder(getActivity())
                        .title(account.name)
                        .description(mAccountManager.getUserData(account, Constants.KEY_HOSTNAME))
                        .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                        .build();

                accountSubActions.add(action);
            }

            accountSubActions.add(new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_NEW_ACCOUNT)
                    .title("Add New Account")
                    .description("")
                    .editable(false)
                    .build()
            );

            if (sAccount != null) {
                accountAction.setDescription(sAccount.name);
                findActionById(ACTION_ID_CONFIRM).setEnabled(true);
            } else {
                findActionById(ACTION_ID_CONFIRM).setEnabled(false);
            }

            notifyActionChanged(findActionPositionById(ACTION_ID_CONFIRM));
        }

        @Override
        public boolean onSubGuidedActionClicked(GuidedAction action) {
            if (action.isChecked()) {
                Log.w(TAG, "WTF: " + action.getTitle().toString());
                sAccount = getAccountByName(action.getTitle().toString());

                findActionById(ACTION_ID_SELECT_ACCOUNT).setDescription(sAccount.name);
                notifyActionChanged(findActionPositionById(ACTION_ID_SELECT_ACCOUNT));

                findActionById(ACTION_ID_CONFIRM).setEnabled(true);
                notifyActionChanged(findActionPositionById(ACTION_ID_CONFIRM));

                return true;
            } else {

                mAccountManager.addAccount(Constants.ACCOUNT_TYPE, null, null, new Bundle(), getActivity(), new AddAccountCallback(), null);
                return true;
            }
        }

        private class AddAccountCallback implements AccountManagerCallback<Bundle> {
            @Override
            public void run(AccountManagerFuture<Bundle> result) {
                onResume();
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (ACTION_ID_CONFIRM == action.getId()) {
                // Setup the client with the selected account
                sClient.setConnectionInfo(sAccount);

                // Move onto the next step
                GuidedStepFragment fragment = new AddChannelsFragment();
                fragment.setArguments(getArguments());
                add(getFragmentManager(), fragment);
            }
        }
    }

    public static class AddChannelsFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_PROCESSING = 1;
        private SyncChannelsTask mSyncChannelsTask;

        @Override
        public void onStart() {
            super.onStart();

            mSyncChannelsTask = new SyncChannelsTask(getActivity()) {
                @Override
                protected void onPostExecute(Boolean completed) {
                    // Move to the SyncEPGFragment
                    GuidedStepFragment fragment = new SyncEPGFragment();
                    fragment.setArguments(getArguments());
                    add(getFragmentManager(), fragment);
                }
            };

            Response.Listener<TVHClient.ChannelList> listener = new Response.Listener<TVHClient.ChannelList>() {
                @Override
                public void onResponse(TVHClient.ChannelList channelList) {
                    mSyncChannelsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, channelList);
                }
            };

            Response.ErrorListener errorListener = new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // Move to the FailedFragment
                    GuidedStepFragment fragment = new FailedFragment();
                    fragment.setArguments(getArguments());
                    add(getFragmentManager(), fragment);
                }
            };

            sClient.getChannelGrid(listener, errorListener);
        }

        @Override
        public void onStop() {
            super.onStop();
            if (mSyncChannelsTask != null) {
                mSyncChannelsTask.cancel(false);
            }
        }

        @Override
        public GuidedActionsStylist onCreateActionsStylist() {
            GuidedActionsStylist stylist = new GuidedActionsStylist() {
                @Override
                public int onProvideItemLayoutId() {
                    return R.layout.setup_progress;
                }

            };
            return stylist;
        }

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard_NoSelector;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Adding Channels",
                    "Just a few seconds please :)",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_PROCESSING)
                    .title("Processing")
                    .infoOnly(true)
                    .build();
            actions.add(action);
        }
    }

    public static class SyncEPGFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_PROCESSING = 1;

        private final BroadcastReceiver mSyncStatusChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {
                 String syncStatus = intent.getStringExtra(Constants.SYNC_STATUS);

                if (syncStatus.equals(Constants.SYNC_FINISHED)) {
                    SyncUtils.setUpPeriodicSync(getActivity());

                    // Move to the CompletedFragment
                    GuidedStepFragment fragment = new CompletedFragment();
                    fragment.setArguments(getArguments());
                    add(getFragmentManager(), fragment);
                }
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                    mSyncStatusChangedReceiver,
                    new IntentFilter(Constants.ACTION_SYNC_STATUS_CHANGED));
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            LocalBroadcastManager.getInstance(getActivity())
                    .unregisterReceiver(mSyncStatusChangedReceiver);
        }

        @Override
        public void onStart() {
            super.onStart();

            // Force a EPG sync
            SyncUtils.cancelAll(getActivity());
            SyncUtils.requestSync(getActivity());
        }

        @Override
        public GuidedActionsStylist onCreateActionsStylist() {
            GuidedActionsStylist stylist = new GuidedActionsStylist() {
                @Override
                public int onProvideItemLayoutId() {
                    return R.layout.setup_progress;
                }

            };
            return stylist;
        }

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard_NoSelector;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Syncing EPG",
                    "Just a few seconds please :)",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_PROCESSING)
                    .title("Processing")
                    .infoOnly(true)
                    .build();
            actions.add(action);
        }
    }

    public static class CompletedFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Setup Complete",
                    "Enjoy!",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            List<GuidedAction> subActions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .title("Complete")
                    .description("You're all set!")
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }
    }

    public static class FailedFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Setup Failed",
                    ":(",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            List<GuidedAction> subActions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .title("Complete")
                    .description("Return to the Live Channels app")
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            getActivity().finish();
        }
    }
}
