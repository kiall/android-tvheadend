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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.text.InputType;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.client.TVHClient;

public class AuthenticatorActivity extends AccountAuthenticatorActivity {
    private static final String TAG = TVHClient.class.getName();

    private AccountManager mAccountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GuidedStepFragment fragment = new ServerFragment();
        fragment.setArguments(getIntent().getExtras());
        GuidedStepFragment.addAsRoot(this, fragment, android.R.id.content);
    }

    @Override
    public void onBackPressed() {
        if (GuidedStepFragment.getCurrentGuidedStepFragment(getFragmentManager())
                instanceof CompletedFragment) {
            finish();

        } else if (GuidedStepFragment.getCurrentGuidedStepFragment(getFragmentManager())
                instanceof FailedFragment) {
            finish();

        } else {
            super.onBackPressed();
        }
    }

    public static abstract class BaseGuidedStepFragment extends GuidedStepFragment {
        protected AccountManager mAccountManager;

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAccountManager = AccountManager.get(getActivity());
        }
    }

    public static class ServerFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_HOSTNAME = 1;
        private static final int ACTION_ID_PORT = 2;
        private static final int ACTION_ID_NEXT = 3;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Tvheadend server",
                    "Enter your Tvheadend server hostname or IP address", null, null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_HOSTNAME)
                    .title("Hostname/IP")
                    .descriptionEditInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
                    .descriptionInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_PORT)
                    .title("Port Number")
                    .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                    .descriptionInputType(InputType.TYPE_CLASS_NUMBER)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_NEXT)
                    .title("Next")
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == ACTION_ID_NEXT) {
                GuidedStepFragment fragment = new AccountFragment();

                Bundle args = getArguments();

                GuidedAction hostnameAction = findActionById(ACTION_ID_HOSTNAME);
                args.putString(Constants.KEY_HOSTNAME, hostnameAction.getDescription().toString());

                GuidedAction portAction = findActionById(ACTION_ID_PORT);
                args.putString(Constants.KEY_PORT, portAction.getDescription().toString());

                fragment.setArguments(args);

                add(getFragmentManager(), fragment);
            }
        }
    }

    public static class AccountFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_USERNAME = 1;
        private static final int ACTION_ID_PASSWORD = 2;
        private static final int ACTION_ID_ADD_ACCOUNT = 3;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Tvheadend Account",
                    "Enter your Tvheadend username and password", null, null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_USERNAME)
                    .title("Username")
                    .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
                    .descriptionInputType(InputType.TYPE_CLASS_TEXT)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_PASSWORD)
                    .title("Password")
                    .descriptionEditInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    .descriptionInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    .descriptionEditable(true)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_ADD_ACCOUNT)
                    .title("Finish")
                    .editable(false)
                    .build();

            actions.add(action);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == ACTION_ID_ADD_ACCOUNT) {
                GuidedStepFragment fragment = new ValidateAccountFragment();

                Bundle args = getArguments();

                GuidedAction usernameAction = findActionById(ACTION_ID_USERNAME);
                args.putString(Constants.KEY_USERNAME, usernameAction.getDescription().toString());

                GuidedAction passwordAction = findActionById(ACTION_ID_PASSWORD);
                args.putString(Constants.KEY_PASSWORD, passwordAction.getDescription().toString());

                fragment.setArguments(args);

                add(getFragmentManager(), fragment);
            }
        }
    }

    public static class ValidateAccountFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_PROCESSING = 1;

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
                    "Tvheadend Account",
                    "Checking your account", null, null);

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

        @Override
        public void onStart() {
            super.onStart();

            Bundle args = getArguments();

            final String accountType = args.getString(AccountManager.KEY_ACCOUNT_TYPE);
            final String accountName = args.getString(Constants.KEY_USERNAME);
            final String accountPassword = args.getString(Constants.KEY_PASSWORD);
            final String accountHostname = args.getString(Constants.KEY_HOSTNAME);
            final String accountPort = args.getString(Constants.KEY_PORT);

            // Validate the User and Pass by connecting to TVHeadend
            Response.Listener<JSONObject> listener = new Response.Listener<JSONObject>() {

                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "Successfully validated credentials");

                    // Store the account
                    final Account account = new Account(accountName, accountType);

                    Bundle userdata = new Bundle();

                    userdata.putString(Constants.KEY_HOSTNAME, accountHostname);
                    userdata.putString(Constants.KEY_PORT, accountPort);

                    mAccountManager.addAccountExplicitly(account, accountPassword, userdata);

                    // Store the result, with the username too
                    userdata.putString(Constants.KEY_USERNAME, accountName);

                    AuthenticatorActivity activity = (AuthenticatorActivity) getActivity();
                    activity.setAccountAuthenticatorResult(userdata);

                    // Move to the CompletedFragment
                    GuidedStepFragment fragment = new CompletedFragment();
                    fragment.setArguments(getArguments());
                    add(getFragmentManager(), fragment);

                }
            };

            Response.ErrorListener errorListener = new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, "Failed to validate credentials");

                    GuidedStepFragment fragment = new FailedFragment();
                    Bundle args = getArguments();

                    if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                        args.putString(Constants.KEY_ERROR_MESSAGE, "Network Timeout!");
                    } else if (error instanceof AuthFailureError) {
                        args.putString(Constants.KEY_ERROR_MESSAGE, "Auth Failure!");
                    } else if (error instanceof ServerError) {
                        args.putString(Constants.KEY_ERROR_MESSAGE, "Unknown Server Error");
                    } else if (error instanceof NetworkError) {
                        args.putString(Constants.KEY_ERROR_MESSAGE, "Unknown Network Error");
                    } else if (error instanceof ParseError) {
                        args.putString(Constants.KEY_ERROR_MESSAGE, "Unknown Parse Error");
                    } else {
                        args.putString(Constants.KEY_ERROR_MESSAGE, "Unknown Error");
                    }

                    fragment.setArguments(args);
                    add(getFragmentManager(), fragment);
                }
            };

            TVHClient client = TVHClient.getInstance(getActivity());

            client.setConnectionInfo(accountHostname, accountPort, accountName, accountPassword);

            client.getServerInfo(listener, errorListener);
        }
    }

    public static class CompletedFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Tvheadend Account",
                    "Successfully Added Account",
                    null,
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
            getActivity().finish();
        }
    }

    public static class FailedFragment extends BaseGuidedStepFragment {
        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "Tvheadend Account",
                    "Failed to add account",
                    null,
                    null);

            return guidance;
        }

        @Override
        public void onResume() {
            super.onResume();

            Bundle args = getArguments();
            String errorMessage = args.getString(Constants.KEY_ERROR_MESSAGE);
            getGuidanceStylist().getDescriptionView().setText("Failed to add account: " + errorMessage);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            List<GuidedAction> subActions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .title("Complete")
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
