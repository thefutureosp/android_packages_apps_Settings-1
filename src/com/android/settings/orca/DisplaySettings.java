/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.orca;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplayStatus;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;
import com.android.settings.Utils;
import com.android.settings.orca.FontDialogPreference;

import org.cyanogenmod.hardware.AdaptiveBacklight;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import java.util.ArrayList;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String STATUS_BAR_BRIGHTNESS_CONTROL = "status_bar_brightness_control";
    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_SWAP_VOLUME_BUTTONS = "swap_volume_buttons";
    private static final String KEY_ROTATION = "rotation";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    private static final String KEY_ADAPTIVE_BACKLIGHT = "adaptive_backlight";
    private static final String KEY_BATTERY_LIGHT = "battery_light";
    private static final String KEY_POWER_CRT_MODE = "system_power_crt_mode";
    private static final String KEY_POWER_CRT_SCREEN_OFF = "system_power_crt_screen_off";
    private static final String KEY_DISPLAY_COLOR = "color_calibration";
    private static final String CATEGORY_LIGHTS = "lights_prefs";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;

    private DisplayManager mDisplayManager;


    private CheckBoxPreference mStatusBarBrightnessControl;

    private CheckBoxPreference mAccelerometer;
    private CheckBoxPreference mSwapVolumeButtons;
    private Preference mRotation;
    private FontDialogPreference mFontSizePref;
    private PreferenceScreen mNotificationPulse;
    private PreferenceScreen mBatteryPulse;
    private ListPreference mCrtMode;
    private CheckBoxPreference mCrtOn;
    private CheckBoxPreference mCrtOff;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private WifiDisplayStatus mWifiDisplayStatus;
    private Preference mWifiDisplayPreference;

    private int mAllowedRotationModes;

    private CheckBoxPreference mAdaptiveBacklight;

    private boolean mIsCrtOffChecked = false;

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };

    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();
        Resources res = getResources();

        addPreferencesFromResource(R.xml.display_settings_orca);
        PreferenceScreen prefSet = getPreferenceScreen();
        mContext = getActivity();

        mStatusBarBrightnessControl = (CheckBoxPreference) prefSet.findPreference(STATUS_BAR_BRIGHTNESS_CONTROL);
        mStatusBarBrightnessControl.setChecked((Settings.System.getInt(getActivity().getApplicationContext().getContentResolver(),
                Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL, 0) == 1));
        try {
            if (Settings.System.getInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                mStatusBarBrightnessControl.setEnabled(false);
                mStatusBarBrightnessControl.setSummary(R.string.status_bar_toggle_info);
            }
        } catch (SettingNotFoundException e) {
        }

        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);

        mSwapVolumeButtons = (CheckBoxPreference) prefSet.findPreference(KEY_SWAP_VOLUME_BUTTONS);
        if (mSwapVolumeButtons != null) {
            if (!Utils.hasVolumeRocker(getActivity())) {
                prefSet.removePreference(mSwapVolumeButtons);
            } else {
                int swapVolumeKeys = Settings.System.getInt(getContentResolver(),
                        Settings.System.SWAP_VOLUME_KEYS_ON_ROTATION, 0);
                mSwapVolumeButtons.setChecked(swapVolumeKeys > 0);
            }
        }

        mRotation = findPreference(KEY_ROTATION);
        mRotation.setOnPreferenceClickListener(this);

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (FontDialogPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mFontSizePref.setOnPreferenceClickListener(this);

        mDisplayManager = (DisplayManager)getActivity().getSystemService(
                Context.DISPLAY_SERVICE);
        mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        mWifiDisplayPreference = (Preference)findPreference(KEY_WIFI_DISPLAY);
        if (mWifiDisplayStatus.getFeatureState()
                == WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE) {
            getPreferenceScreen().removePreference(mWifiDisplayPreference);
            mWifiDisplayPreference = null;
        }

        mAdaptiveBacklight = (CheckBoxPreference) findPreference(KEY_ADAPTIVE_BACKLIGHT);
        if (!isAdaptiveBacklightSupported()) {
            getPreferenceScreen().removePreference(mAdaptiveBacklight);
            mAdaptiveBacklight = null;
        }

        boolean hasNotificationLed = res.getBoolean(
                com.android.internal.R.bool.config_intrusiveNotificationLed);
        boolean hasBatteryLed = res.getBoolean(
                com.android.internal.R.bool.config_intrusiveBatteryLed);
        PreferenceCategory lightPrefs = (PreferenceCategory) findPreference(CATEGORY_LIGHTS);

        if (hasNotificationLed || hasBatteryLed) {
            mBatteryPulse = (PreferenceScreen) findPreference(KEY_BATTERY_LIGHT);
            mNotificationPulse = (PreferenceScreen) findPreference(KEY_NOTIFICATION_PULSE);

            // Battery light is only for primary user
            if (UserHandle.myUserId() != UserHandle.USER_OWNER || !hasBatteryLed) {
                lightPrefs.removePreference(mBatteryPulse);
                mBatteryPulse = null;
            }

            if (!hasNotificationLed) {
                lightPrefs.removePreference(mNotificationPulse);
                mNotificationPulse = null;
            }
        } else {
            getPreferenceScreen().removePreference(lightPrefs);
        }

        if (Utils.isTablet()) {
            prefSet.removePreference(mStatusBarBrightnessControl);
        }

        // respect device default configuration
        // true fades while false animates
        boolean electronBeamFadesConfig = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_animateScreenLights);

        // use this to enable/disable crt on feature
        mIsCrtOffChecked = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.SYSTEM_POWER_ENABLE_CRT_OFF,
                electronBeamFadesConfig ? 0 : 1) == 1;

        mCrtOff = (CheckBoxPreference) findPreference(KEY_POWER_CRT_SCREEN_OFF);
        mCrtOff.setChecked(mIsCrtOffChecked);

        mCrtMode = (ListPreference) prefSet.findPreference(KEY_POWER_CRT_MODE);
        int crtMode = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.SYSTEM_POWER_CRT_MODE, 0);
        mCrtMode.setValue(String.valueOf(crtMode));
        mCrtMode.setSummary(mCrtMode.getEntry());
        mCrtMode.setOnPreferenceChangeListener(this);
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                    }
                }
                summary = preference.getContext().getString(R.string.screen_timeout_summary,
                        entries[best]);
            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    private void updateBatteryPulseDescription() {
        if (Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.BATTERY_LIGHT_ENABLED, 1) == 1) {
            mBatteryPulse.setSummary(getString(R.string.notification_light_enabled));
        } else {
            mBatteryPulse.setSummary(getString(R.string.notification_light_disabled));
        }
     }

    @Override
    public void onResume() {
        super.onResume();

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        if (mWifiDisplayPreference != null) {
            getActivity().registerReceiver(mReceiver, new IntentFilter(
                    DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED));
            mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        }

        if (mAdaptiveBacklight != null) {
            mAdaptiveBacklight.setChecked(AdaptiveBacklight.isEnabled());
        }

        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        if (mWifiDisplayPreference != null) {
            getActivity().unregisterReceiver(mReceiver);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }

    private void updateState() {
        readFontSizePreference(mFontSizePref);
        updateAccelerometerRotationCheckbox();
        updateScreenSaverSummary();
        updateWifiDisplaySummary();
        updateLightPulseSummary();
        updateBatteryPulseSummary();
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateWifiDisplaySummary() {
        if (mWifiDisplayPreference != null) {
            switch (mWifiDisplayStatus.getFeatureState()) {
                case WifiDisplayStatus.FEATURE_STATE_OFF:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_off);
                    break;
                case WifiDisplayStatus.FEATURE_STATE_ON:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_on);
                    break;
                case WifiDisplayStatus.FEATURE_STATE_DISABLED:
                default:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_disabled);
                    break;
            }
        }
    }

    private void updateLightPulseSummary() {
        if (mNotificationPulse != null) {
            if (Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_PULSE, 0) == 1) {
                mNotificationPulse.setSummary(R.string.notification_light_enabled);
            } else {
                mNotificationPulse.setSummary(R.string.notification_light_disabled);
            }
        }
    }

    private void updateBatteryPulseSummary() {
        if (mBatteryPulse != null) {
            if (Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.BATTERY_LIGHT_ENABLED, 1) == 1) {
                mBatteryPulse.setSummary(R.string.notification_light_enabled);
            } else {
                mBatteryPulse.setSummary(R.string.notification_light_disabled);
            }
        }
    }

    /**
     * Reads the current font size and sets the value in the summary text
     */
    public void readFontSizePreference(Preference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // report the current size in the summary text
        final Resources res = getResources();
        String fontDesc = FontDialogPreference.getFontSizeDescription(res, mCurConfig.fontScale);
        pref.setSummary(getString(R.string.summary_font_size, fontDesc));
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean value;

        if (preference == mStatusBarBrightnessControl) {
        value = mStatusBarBrightnessControl.isChecked();
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL,
                    value ? 1 : 0);
            return true;
        } else if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(
                    getActivity(), !mAccelerometer.isChecked());
        } else if (preference == mSwapVolumeButtons) {
            Context context = getActivity().getApplicationContext();
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SWAP_VOLUME_KEYS_ON_ROTATION,
                    mSwapVolumeButtons.isChecked()
                    ? (Utils.isTablet() ? 2 : 1)
                    : 0);
            return true;
        } else if (preference == mCrtOff) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SYSTEM_POWER_ENABLE_CRT_OFF,
                    mCrtOff.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mAdaptiveBacklight) {
            return AdaptiveBacklight.setEnabled(mAdaptiveBacklight.isChecked());
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }

        if (preference == mCrtMode) {
            int crtMode = Integer.valueOf((String) objValue);
            int index = mCrtMode.findIndexOfValue((String) objValue);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SYSTEM_POWER_CRT_MODE, crtMode);
            mCrtMode.setSummary(mCrtMode.getEntries()[index]);
            return true;
	}
        return true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                mWifiDisplayStatus = (WifiDisplayStatus)intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                updateWifiDisplaySummary();
            }
        }
    };

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFontSizePref) {
            if (Utils.hasMultipleUsers(getActivity())) {
                showDialog(DLG_GLOBAL_CHANGE_WARNING);
                return true;
            } else {
                mFontSizePref.click();
            }
        } else if (preference == mRotation) {
            mAllowedRotationModes = Settings.System.getInt(getActivity().getContentResolver(), Settings.System
                    .ACCELEROMETER_ROTATION_ANGLES, -1);
            if (mAllowedRotationModes < 0) {
                mAllowedRotationModes = Utils.getScreenType(getActivity())
                        == Utils.DEVICE_TABLET ? (1 | 2 | 4 | 8) :  (1 | 2 | 8);
            }
            AlertDialog.Builder d = new AlertDialog.Builder(getActivity());
            d.setTitle(R.string.rotation_settings_title);
            String[] entries = getResources().getStringArray(R.array.rotation_mode_entries);
            final String[] values = getResources().getStringArray(R.array.rotation_mode_values);
            boolean[] selectedRotations = {(mAllowedRotationModes & 1) != 0, (mAllowedRotationModes & 2) != 0,
                    (mAllowedRotationModes & 4) != 0, (mAllowedRotationModes & 8) != 0};
            d.setMultiChoiceItems(entries, selectedRotations, new OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    int value = Integer.parseInt(values[which]);
                    if(isChecked) {
                        mAllowedRotationModes |= value;
                    } else {
                        mAllowedRotationModes &= ~value;
                    }
                }
            });
            d.setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Settings.System.putInt(getActivity().getContentResolver(), Settings.System
                            .ACCELEROMETER_ROTATION_ANGLES, mAllowedRotationModes);
                }
            });
            d.show();
            return true;
        }
        return false;
    }

    /**
     * Restore the properties associated with this preference on boot
     * @param ctx A valid context
     */
    public static void restore(Context ctx) {
        if (isAdaptiveBacklightSupported()) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            final boolean enabled = prefs.getBoolean(KEY_ADAPTIVE_BACKLIGHT, true);
            if (!AdaptiveBacklight.setEnabled(enabled)) {
                Log.e(TAG, "Failed to restore adaptive backlight settings.");
            } else {
                Log.d(TAG, "Adaptive backlight settings restored.");
            }
        }
    }

    private static boolean isAdaptiveBacklightSupported() {
        try {
            return AdaptiveBacklight.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return false;
        }
    }
}
