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
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.client.TVHClient;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.sync.SyncJobService;
import ie.macinnes.tvheadend.sync.SyncUtils;
import ie.macinnes.tvheadend.utils.TvContractUtils;

public class TvInputSetupFragment extends DetailsFragment {
    private static final String TAG = TvInputSetupFragment.class.getName();

    private String mInputId;
    private AccountManager mAccountManager;
    private TVHClient mClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        mAccountManager = AccountManager.get(getActivity());
        mClient = TVHClient.getInstance(getActivity());

        setClientConnectionInfo();
        findChannels();
    }

    private void setClientConnectionInfo() {
        Account[] accounts = mAccountManager.getAccountsByType("ie.macinnes.tvheadend");

        // TODO: We should only every have one account.. Figure out how that works (or 1 account per
        //       hostname+port combo?)
        for (Account account : accounts) {
            mClient.setConnectionInfo(account);
        }
    }

    private void findChannels() {
        Response.Listener<TVHClient.ChannelList> listener = new Response.Listener<TVHClient.ChannelList>() {

            @Override
            public void onResponse(TVHClient.ChannelList channelList) {
                TvContractUtils.updateChannels(
                        getActivity(),
                        mInputId,
                        ChannelList.fromClientChannelList(channelList, mInputId));

                // Set up SharedPreference to store inputId, used by the BootReceiver to set up the
                // periodic sync job again after a reboot.
                SharedPreferences sharedPreferences = getActivity().getSharedPreferences(
                        Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(Constants.KEY_INPUT_ID, mInputId);
                editor.apply();

                // TODO: Show UI, and only finish when sync completes.
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();

                // Force a EPG sync
                SyncUtils.cancelAll(getActivity());
                SyncUtils.requestSync(getActivity(), mInputId);
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                onError("Failed to retreive channels");
            }
        };

        mClient.getChannelGrid(listener, errorListener);
    }

    private void onError(int errorResId) {
        Toast.makeText(getActivity(), errorResId, Toast.LENGTH_SHORT).show();
        getActivity().finish();
    }

    private void onError(String errorMessage) {
        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        getActivity().finish();
    }
}
