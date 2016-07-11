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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.InputType;

import java.util.ArrayList;
import java.util.List;

import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.R;
import ie.macinnes.tvheadend.migrate.MigrateUtils;

public class VlcSetupActivity extends Activity {
    private static final String TAG = VlcSetupActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Find a better (+ out of UI thread) way to do this.
        MigrateUtils.doMigrate(getBaseContext());

        GuidedStepFragment fragment = new AdvancedSettingsFragment();
        fragment.setArguments(getIntent().getExtras());
        GuidedStepFragment.addAsRoot(this, fragment, android.R.id.content);
    }

    public static abstract class BaseGuidedStepFragment extends GuidedStepFragment {
        protected SharedPreferences mSharedPreferences;

        @Override
        public int onProvideTheme() {
            return R.style.Theme_Wizard;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mSharedPreferences = getActivity().getSharedPreferences(
                Constants.PREFERENCE_TVHEADEND, Context.MODE_PRIVATE);
        }
    }

    public static class AdvancedSettingsFragment extends BaseGuidedStepFragment {
        private static final int ACTION_ID_CONFIRM = 1;

        // Deinterlace Methods are 2xx
        private static final int ACTION_ID_SELECT_DEINTERLACE_METHOD = 2;
        private static final int ACTION_ID_DEINTERLACE_BLEND = 201;
        private static final int ACTION_ID_DEINTERLACE_MEAN = 202;
        private static final int ACTION_ID_DEINTERLACE_BOB = 203;
        private static final int ACTION_ID_DEINTERLACE_LINEAR = 204;
        private static final int ACTION_ID_DEINTERLACE_YADIF = 205;
        private static final int ACTION_ID_DEINTERLACE_YADIF2X = 206;
        private static final int ACTION_ID_DEINTERLACE_PHOSPHOR = 207;
        private static final int ACTION_ID_DEINTERLACE_IVTC = 208;
        private static final int ACTION_ID_DEINTERLACE_DISABLE = 299;

        // Scaling Methods are 3xx
        private static final int ACTION_ID_SELECT_SCALING_METHOD = 3;
        private static final int ACTION_ID_FAST_SCALING_BILINEAR = 301;
        private static final int ACTION_ID_SCALING_BILINEAR = 302;
        private static final int ACTION_ID_SCALING_BICUBIC = 303;
        private static final int ACTION_ID_SCALING_AREA = 304;
        private static final int ACTION_ID_SCALING_LUMA_BICUBIC = 305;
        private static final int ACTION_ID_SCALING_GAUSS = 306;
        private static final int ACTION_ID_SCALING_SINCR = 307;
        private static final int ACTION_ID_SCALING_LANCZOS = 308;
        private static final int ACTION_ID_SCALING_BICUBIC_SPLINE = 309;
        private static final int ACTION_ID_SCALING_DISABLE = 399;

        // Hardware Acceleration are 4xx
        private static final int ACTION_ID_SELECT_HW_ACCEL = 4;
        private static final int ACTION_ID_HW_ACCEL_AUTOMATIC = 401;
        private static final int ACTION_ID_HW_ACCEL_ENABLED = 402;
        private static final int ACTION_ID_HW_ACCEL_DISABLED = 403;

        // Network Buffering
        private static final int ACTION_ID_SELECT_NETWORK_BUFFER = 5;

        private Boolean mDeinterlace = false;
        private String mDeinterlaceMethod;
        private Boolean mScaling = false;
        private int mScalingMethod;
        private int mHwAccelMethod;
        private int mNetworkBuffer;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(
                    "LibVLC Settings",
                    "Advanced LibVLC Settings",
                    "TVHeadend",
                    null);

            return guidance;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SELECT_DEINTERLACE_METHOD)
                    .title("Deinterlacing")
                    .description("Choose a deinterlacing method to use")
                    .subActions(createDeinterlaceSubActions())
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SELECT_SCALING_METHOD)
                    .title("Scaling")
                    .description("Choose a scaling method to use")
                    .subActions(createScalingSubActions())
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SELECT_HW_ACCEL)
                    .title("Hardware Acceleration")
                    .description("Choose a hardware acceleration mode")
                    .subActions(createHardwareAccelerationSubActions())
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SELECT_NETWORK_BUFFER)
                    .title("Network Buffering (in ms)")
                    .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                    .descriptionInputType(InputType.TYPE_CLASS_NUMBER)
                    .descriptionEditable(true)
                    .editDescription("2000")
                    .build();

            actions.add(action);
        }

        @Override
        public void onCreateButtonActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_CONFIRM)
                    .title("Confirm")
                    .build();

            actions.add(action);
        }

        protected List<GuidedAction> createDeinterlaceSubActions() {
            List<GuidedAction> actions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_BLEND)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Blend")
                    .description("Recommended")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_MEAN)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Mean")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_BOB)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Bob")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_LINEAR)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Linear")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_YADIF)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Yadif")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_YADIF2X)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Yadif2x")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_PHOSPHOR)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Phosphor")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_IVTC)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Ivtc")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_DEINTERLACE_DISABLE)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Disable")
                    .build();

            actions.add(action);

            return actions;
        }

        protected List<GuidedAction> createScalingSubActions() {
            List<GuidedAction> actions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_FAST_SCALING_BILINEAR)
                    .title("Fast Bilinear")
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_BILINEAR)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Bilinear")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_BICUBIC)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Bicubic")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_AREA)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Area")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_LUMA_BICUBIC)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Luma Bicubic")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_GAUSS)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Gauss")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_SINCR)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("SincR")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_LANCZOS)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Lanczos")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_BICUBIC_SPLINE)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Bicubic Spline")
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_SCALING_DISABLE)
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .title("Disable")
                    .build();

            actions.add(action);

            return actions;
        }

        protected List<GuidedAction> createHardwareAccelerationSubActions() {
            List<GuidedAction> actions = new ArrayList();

            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_HW_ACCEL_AUTOMATIC)
                    .title("Automatic")
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_HW_ACCEL_ENABLED)
                    .title("Enabled")
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .build();

            actions.add(action);

            action = new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_HW_ACCEL_DISABLED)
                    .title("Disabled")
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .build();

            actions.add(action);

            return actions;
        }

        @Override
        public boolean onSubGuidedActionClicked(GuidedAction action) {
            long actionId = action.getId();

            if (actionId >= 200 && actionId <= 299) {
                // A deinterlace method was chosen
                if (action.getId() == ACTION_ID_DEINTERLACE_BLEND) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_BLEND;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_MEAN) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_MEAN;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_BOB) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_BOB;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_LINEAR) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_LINEAR;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_YADIF) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_YADIF;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_YADIF2X) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_YADIF2X;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_PHOSPHOR) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_PHOSPHOR;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_IVTC) {
                    mDeinterlace = true;
                    mDeinterlaceMethod = Constants.DEINTERLACE_IVTC;
                } else if (action.getId() == ACTION_ID_DEINTERLACE_DISABLE) {
                    mDeinterlace = false;
                } else {
                    // Unknown method
                    throw new RuntimeException("Unknown deinterlace method selected");
                }
            } else if (actionId >= 300 && actionId <= 399) {
                // A scaling method was chosen
                if (action.getId() == ACTION_ID_FAST_SCALING_BILINEAR) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_FAST_BILINEAR;
                } else if (action.getId() == ACTION_ID_SCALING_BILINEAR) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_BILINEAR;
                }  else if (action.getId() == ACTION_ID_SCALING_BICUBIC) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_BICUBIC;
                } else if (action.getId() == ACTION_ID_SCALING_AREA) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_AREA;
                } else if (action.getId() == ACTION_ID_SCALING_LUMA_BICUBIC) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_LUMA_BICUBIC;
                } else if (action.getId() == ACTION_ID_SCALING_GAUSS) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_GAUSS;
                } else if (action.getId() == ACTION_ID_SCALING_SINCR) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_SINCR;
                } else if (action.getId() == ACTION_ID_SCALING_LANCZOS) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_LANCZOS;
                } else if (action.getId() == ACTION_ID_SCALING_BICUBIC_SPLINE) {
                    mScaling = true;
                    mScalingMethod = Constants.SCALING_BICUBIC_SPLINE;
                } else if (action.getId() == ACTION_ID_SCALING_DISABLE) {
                    mScaling = false;
                } else {
                    // Unknown method
                    throw new RuntimeException("Unknown scaling method selected");
                }
            } else if (actionId >= 400 && actionId <= 499) {
                // A HW accel method was chosen
                if (action.getId() == ACTION_ID_HW_ACCEL_AUTOMATIC) {
                    mHwAccelMethod = Constants.HW_ACCEL_AUTOMATIC;
                } else if (action.getId() == ACTION_ID_HW_ACCEL_ENABLED) {
                    mHwAccelMethod = Constants.HW_ACCEL_ENABLED;
                }  else if (action.getId() == ACTION_ID_HW_ACCEL_DISABLED) {
                    mHwAccelMethod = Constants.HW_ACCEL_DISABLED;
                } else {
                    // Unknown method
                    throw new RuntimeException("Unknown hardware acceleration mode selected");
                }
            } else if (actionId == ACTION_ID_SELECT_NETWORK_BUFFER) {
                mNetworkBuffer = Integer.parseInt(action.getDescription().toString());
            }

            if (action.isChecked()) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (ACTION_ID_CONFIRM == action.getId()) {
                // Save the chosen preferences
                SharedPreferences.Editor editor = mSharedPreferences.edit();

                editor.putBoolean(Constants.KEY_DEINTERLACE_ENABLED, mDeinterlace);
                if (mScaling) editor.putString(Constants.KEY_DEINTERLACE_METHOD, mDeinterlaceMethod);

                editor.putBoolean(Constants.KEY_SCALING_ENABLED, mScaling);
                if (mScaling) editor.putInt(Constants.KEY_SCALING_METHOD, mScalingMethod);

                editor.putInt(Constants.KEY_HW_ACCEL_METHOD, mHwAccelMethod);

                editor.putInt(Constants.KEY_NETWORK_BUFFER, mNetworkBuffer);

                editor.commit();

                // Complete the VlcSetupWizard
                getActivity().finish();
            }
        }
    }
}
