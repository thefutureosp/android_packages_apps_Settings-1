/*
 * Copyright (C) 2013 Slimroms
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

package com.android.settings.cyanogenmod.quicksettings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.util.Helpers; 

import net.margaritov.preference.colorpicker.ColorPickerPreference;

public class QuickSettingsTilesStyle extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String PREF_TILES_PER_ROW = "tiles_per_row";
    private static final String PREF_TILES_PER_ROW_DUPLICATE_LANDSCAPE = "tiles_per_row_duplicate_landscape";
    private static final String PREF_QUICK_TILES_TEXT_COLOR = "quick_tiles_text_color";
    private static final String PREF_QUICK_TILES_BG_COLOR = "quick_tiles_bg_color";
    private static final String PREF_QUICK_TILES_BG_PRESSED_COLOR = "quick_tiles_bg_pressed_color";
    private static final String PREF_FLIP_QS_TILES = "flip_qs_tiles";
    private static final String PREF_QS_TILES_RANDOM = "qs_tiles_random";

    private static final int DEFAULT_QUICK_TILES_TEXT_COLOR = 0xffcccccc;
    private static final int DEFAULT_QUICK_TILES_BG_COLOR = 0xff161616;
    private static final int DEFAULT_QUICK_TILES_BG_PRESSED_COLOR = 0xff212121;


    private ListPreference mTilesPerRow;
    private CheckBoxPreference mDuplicateColumnsLandscape;
    private ColorPickerPreference mQuickTilesTextColor;
    private ColorPickerPreference mQuickTilesBgColor;
    private ColorPickerPreference mQuickTilesBgPressedColor;
    private CheckBoxPreference mFlipQsTiles;
    private CheckBoxPreference mQsTilesRandom;

    private boolean mCheckPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshSettings();
    }

    private PreferenceScreen refreshSettings() {
        mCheckPreferences = false;
        PreferenceScreen prefs = getPreferenceScreen();
        if (prefs != null) {
            prefs.removeAll();
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.quicksettings_tiles_style);

        prefs = getPreferenceScreen();

        mQuickTilesBgColor = (ColorPickerPreference) findPreference(PREF_QUICK_TILES_BG_COLOR);
        mQuickTilesBgColor.setNewPreviewColor(DEFAULT_QUICK_TILES_BG_COLOR);
        mQuickTilesBgColor.setOnPreferenceChangeListener(this);
        int intColor = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_TILES_BG_COLOR, -2);
        if (intColor == -2) {
            mQuickTilesBgColor.setSummary(getResources().getString(R.string.none));
        } else {
            mQuickTilesBgColor.setNewPreviewColor(intColor);
        }

        mQuickTilesBgPressedColor = (ColorPickerPreference) findPreference(PREF_QUICK_TILES_BG_PRESSED_COLOR);
        mQuickTilesBgPressedColor.setNewPreviewColor(DEFAULT_QUICK_TILES_BG_PRESSED_COLOR);
        mQuickTilesBgPressedColor.setOnPreferenceChangeListener(this);
        intColor = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_TILES_BG_PRESSED_COLOR, -2);
        if (intColor == -2) {
            mQuickTilesBgPressedColor.setSummary(getResources().getString(R.string.none));
        } else {
            mQuickTilesBgPressedColor.setNewPreviewColor(intColor);
        }

        mQuickTilesTextColor = (ColorPickerPreference) findPreference(PREF_QUICK_TILES_TEXT_COLOR);
        mQuickTilesTextColor.setNewPreviewColor(DEFAULT_QUICK_TILES_TEXT_COLOR);
        mQuickTilesTextColor.setOnPreferenceChangeListener(this);
        intColor = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_TILES_TEXT_COLOR, -2);
        if (intColor == -2) {
            mQuickTilesTextColor.setSummary(getResources().getString(R.string.none));
        } else {
            mQuickTilesTextColor.setNewPreviewColor(intColor);
        }

        mTilesPerRow = (ListPreference) prefs.findPreference(PREF_TILES_PER_ROW);
        int tilesPerRow = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.QUICK_TILES_PER_ROW, 3);
        mTilesPerRow.setValue(String.valueOf(tilesPerRow));
        mTilesPerRow.setSummary(mTilesPerRow.getEntry());
        mTilesPerRow.setOnPreferenceChangeListener(this);

        mDuplicateColumnsLandscape = (CheckBoxPreference) findPreference(PREF_TILES_PER_ROW_DUPLICATE_LANDSCAPE);
        mDuplicateColumnsLandscape.setChecked(Settings.System.getInt(
                getActivity().getContentResolver(),
                Settings.System.QUICK_TILES_PER_ROW_DUPLICATE_LANDSCAPE, 1) == 1);

        mFlipQsTiles = (CheckBoxPreference) findPreference(PREF_FLIP_QS_TILES);
        mFlipQsTiles.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.QUICK_SETTINGS_TILES_FLIP, 1) == 1);

        mQsTilesRandom = (CheckBoxPreference) findPreference(PREF_QS_TILES_RANDOM);
        mQsTilesRandom.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.QUICK_TILES_BG_COLOR_RANDOM, 0) == 1);

        setHasOptionsMenu(true);
        mCheckPreferences = true;
        return prefs;
    }
 
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.quick_settings_tiles_style, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reset:
                Settings.System.putInt(getActivity().getContentResolver(),
                        Settings.System.QUICK_TILES_TEXT_COLOR, -2);
                Settings.System.putInt(getActivity().getContentResolver(),
                        Settings.System.QUICK_TILES_BG_COLOR, -2);
                Settings.System.putInt(getActivity().getContentResolver(),
                        Settings.System.QUICK_TILES_BG_PRESSED_COLOR, -2);
                refreshSettings();
                return true;
             default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mDuplicateColumnsLandscape) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_TILES_PER_ROW_DUPLICATE_LANDSCAPE,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
            Helpers.restartSystemUI();
            return true;
        } else if (preference == mFlipQsTiles) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_SETTINGS_TILES_FLIP,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
            return true;
        } else if (preference == mQsTilesRandom) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_TILES_BG_COLOR_RANDOM,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
            Helpers.restartSystemUI();
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!mCheckPreferences) {
            return false;
        }
        if (preference == mTilesPerRow) {
            int index = mTilesPerRow.findIndexOfValue((String) newValue);
            int value = Integer.valueOf((String) newValue);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.QUICK_TILES_PER_ROW,
                    value);
            mTilesPerRow.setSummary(mTilesPerRow.getEntries()[index]);
            Helpers.restartSystemUI();
            return true;
        } else if (preference == mQuickTilesTextColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.QUICK_TILES_TEXT_COLOR,
                    intHex);
            return true;
        } else if (preference == mQuickTilesBgColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getContentResolver(),
                   Settings.System.QUICK_TILES_BG_COLOR,
                   intHex);
           Helpers.restartSystemUI();
           return true;
        } else if (preference == mQuickTilesBgPressedColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getContentResolver(),
                   Settings.System.QUICK_TILES_BG_PRESSED_COLOR, 
                   intHex);
           return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

}
