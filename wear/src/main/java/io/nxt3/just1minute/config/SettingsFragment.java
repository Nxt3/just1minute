package io.nxt3.just1minute.config;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.support.annotation.XmlRes;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.widget.Toast;

import com.google.android.wearable.intent.RemoteIntent;

import java.util.ArrayList;
import java.util.concurrent.Executor;

import io.nxt3.just1minute.BuildConfig;
import io.nxt3.just1minute.Just1MinuteWatchFaceService;
import io.nxt3.just1minute.R;

import static android.app.Activity.RESULT_OK;


public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final String TAG = "Settings";

    private Context mContext;

    //Normal color request codes
    private final int HOUR_TICK_COLOR_REQ = 10;
    private final int TICK_COLOR_REQ = 11;
    private final int MINUTE_TEXT_COLOR_REQ = 12;
    private final int BACKGROUND_COLOR_REQ = 13;
    private final int COMPLICATION_COLOR_REQ = 14;

    //Night mode request codes
    private final int HOUR_TICK_NIGHT_MODE_COLOR_REQ = 15;
    private final int TICK_NIGHT_MODE_COLOR_REQ = 16;
    private final int MINUTE_TEXT_NIGHT_MODE_COLOR_REQ = 17;
    private final int BACKGROUND_NIGHT_MODE_COLOR_REQ = 19;
    private final int COMPLICATION_NIGHT_MODE_COLOR_REQ = 20;

    private boolean mAutoNightModeEnabled;
    private boolean mManualNightModeEnabled;

    private ProviderInfoRetriever mProviderInfoRetriever;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getContext();

        addPreferencesFromResource(R.xml.settings);
        updateAll();

        //Disable the inverse of each of these Night mode settings
        //(only one can be enabled at any time)
        mAutoNightModeEnabled = getPreferenceScreen().getSharedPreferences()
                .getBoolean("settings_night_mode_enabled", false);
        mManualNightModeEnabled = getPreferenceScreen().getSharedPreferences()
                .getBoolean("settings_night_mode_manual_enabled", false);
        findPreference("settings_night_mode_manual_enabled")
                .setEnabled(!mAutoNightModeEnabled);
        findPreference("settings_night_mode_enabled")
                .setEnabled(!mManualNightModeEnabled);

        //Set the current version
        getPreferenceScreen().findPreference("app_version").setSummary(BuildConfig.VERSION_NAME);
    }

    @Override
    public void addPreferencesFromResource(@XmlRes int preferencesResId) {
        super.addPreferencesFromResource(preferencesResId);

        Executor executor = (runnable) -> new Thread(runnable).start();

        ProviderInfoRetriever.OnProviderInfoReceivedCallback callback = new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
            @Override
            public void onProviderInfoReceived(int i, @Nullable ComplicationProviderInfo complicationProviderInfo) {
                setComplicationSummary(i, complicationProviderInfo);
            }
        };

        mProviderInfoRetriever = new ProviderInfoRetriever(mContext, executor);

        mProviderInfoRetriever.init();
        mProviderInfoRetriever.retrieveProviderInfo(callback,
                new ComponentName(mContext, Just1MinuteWatchFaceService.class),
                Just1MinuteWatchFaceService.COMPLICATION_IDS);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Bundle extras = preference.getExtras();

        final SharedPreferences.Editor editor
                = getPreferenceScreen().getSharedPreferences().edit();

        //Default colors
        final int defaultHour = mContext.getColor(R.color.default_current_hour_tick);
        final int defaultTick = mContext.getColor(R.color.default_hour_tick);
        final int defaultMinutes = mContext.getColor(R.color.default_minute_text);
        final int defaultBackground = mContext.getColor(R.color.default_background);
        final int defaultComplications = mContext.getColor(R.color.default_complications);

        //Default night mode colors
        final int defaultNightModeHour = mContext.getColor(R.color.default_hour_tick_night_mode);
        final int defaultNightModeTick = mContext.getColor(R.color.default_hour_tick_night_mode);
        final int defaultNightModeMinutes = mContext.getColor(R.color.default_minute_text_night_mode);
        final int defaultNightModeBackground = mContext.getColor(R.color.default_background_night_mode);
        final int defaultNightModeComplications = mContext.getColor(R.color.default_complications_night_mode);

        switch (preference.getKey()) {
            case "settings_top_complication":
            case "settings_bottom_complication":
            case "settings_wallpaper_complication":
                final int id = extras.getInt("id");
                startActivityForResult(
                        ComplicationHelperActivity.createProviderChooserHelperIntent(
                                mContext,
                                new ComponentName(mContext.getApplicationContext(),
                                        Just1MinuteWatchFaceService.class),
                                id,
                                getSupportedComplicationTypes(id)), id);
                break;

            case "settings_complication_color":
                createColorPreferenceActivityIntent(mContext, "settings_complication_color_value",
                        defaultComplications, COMPLICATION_COLOR_REQ);
                break;

            case "settings_complication_night_mode_color":
                createColorPreferenceActivityIntent(mContext,
                        "settings_complication_night_mode_color_value",
                        defaultNightModeComplications, COMPLICATION_NIGHT_MODE_COLOR_REQ);
                break;

            case "settings_hour_tick_color":
                createColorPreferenceActivityIntent(mContext, "settings_hour_tick_color_value",
                        defaultHour, HOUR_TICK_COLOR_REQ);
                break;

            case "settings_hour_tick_night_mode_color":
                createColorPreferenceActivityIntent(mContext,
                        "settings_hour_tick_night_mode_color_value",
                        defaultNightModeHour, HOUR_TICK_NIGHT_MODE_COLOR_REQ);
                break;

            case "settings_tick_color":
                createColorPreferenceActivityIntent(mContext, "settings_tick_color_value",
                        defaultTick, TICK_COLOR_REQ);
                break;

            case "settings_tick_night_mode_color":
                createColorPreferenceActivityIntent(mContext,
                        "settings_tick_night_mode_color_value",
                        defaultNightModeTick, TICK_NIGHT_MODE_COLOR_REQ);
                break;

            case "settings_minute_text_color":
                createColorPreferenceActivityIntent(mContext, "settings_minute_text_color_value",
                        defaultMinutes, MINUTE_TEXT_COLOR_REQ);
                break;

            case "settings_minute_text_night_mode_color":
                createColorPreferenceActivityIntent(mContext,
                        "settings_minute_text_night_mode_color_value",
                        defaultNightModeMinutes, MINUTE_TEXT_NIGHT_MODE_COLOR_REQ);
                break;

            case "settings_background_color":
                createColorPreferenceActivityIntent(mContext, "settings_background_color_value",
                        defaultBackground, BACKGROUND_COLOR_REQ);
                break;

            case "settings_background_night_mode_color":
                createColorPreferenceActivityIntent(mContext,
                        "settings_background_night_mode_color_value",
                        defaultNightModeBackground, BACKGROUND_NIGHT_MODE_COLOR_REQ);
                break;

            case "settings_night_mode_enabled":
                //If auto night mode is enabled, disable manual night mode
                mAutoNightModeEnabled = getPreferenceScreen().getSharedPreferences()
                        .getBoolean("settings_night_mode_enabled", false);
                findPreference("settings_night_mode_manual_enabled")
                        .setEnabled(!mAutoNightModeEnabled);

                editor.putBoolean("force_night_mode", false).apply();
                editor.putBoolean("settings_night_mode_manual_enabled", false).commit();

                break;

            case "settings_night_mode_manual_enabled":
                //If manual night mode is enabled, disable auto night mode
                mManualNightModeEnabled = getPreferenceScreen().getSharedPreferences()
                        .getBoolean("settings_night_mode_manual_enabled", false);
                findPreference("settings_night_mode_enabled").setEnabled(!mManualNightModeEnabled);

                editor.putBoolean("force_night_mode", false).apply();
                editor.putBoolean("settings_night_mode_enabled", false).commit();

                break;

            case "settings_reset_colors":
                editor.putString("settings_complication_color", getString(R.string.settings_white));
                editor.putInt("settings_complication_color_value", defaultHour);

                editor.putString("settings_hour_tick_color", getString(R.string.settings_white));
                editor.putInt("settings_hour_tick_color_value", defaultHour);

                editor.putString("settings_tick_color", getString(R.string.settings_dark_gray));
                editor.putInt("settings_tick_color_value", defaultTick);

                editor.putString("settings_minute_text_color", getString(R.string.settings_white));
                editor.putInt("settings_minute_text_color_value", defaultMinutes);

                editor.putString("settings_background_color", getString(R.string.settings_black));
                editor.putInt("settings_background_color_value", defaultBackground);

                editor.apply();
                setSummary("settings_complication_color");
                setSummary("settings_hour_tick_color");
                setSummary("settings_tick_color");
                setSummary("settings_minute_text_color");
                setSummary("settings_background_color");

                Toast.makeText(mContext,
                        getString(R.string.settings_confirmation_colors_reset_toast),
                        Toast.LENGTH_SHORT).show();
                break;

            case "settings_reset_night_mode_colors":
                editor.putString("settings_complication_night_mode_color", getString(R.string.settings_dark_gray));
                editor.putInt("settings_complication_night_mode_color_value", defaultNightModeComplications);

                editor.putString("settings_hour_tick_night_mode_color", getString(R.string.settings_default_color));
                editor.putInt("settings_hour_tick_night_mode_color_value", defaultNightModeHour);

                editor.putString("settings_tick_night_mode_color", getString(R.string.settings_dark_gray));
                editor.putInt("settings_tick_night_mode_color_value", defaultNightModeTick);

                editor.putString("settings_minute_text_night_mode_color", getString(R.string.settings_default_color));
                editor.putInt("settings_minute_text_night_mode_color_value", defaultNightModeMinutes);

                editor.putString("settings_background_night_mode_color", getString(R.string.settings_black));
                editor.putInt("settings_background_night_mode_color_value", defaultNightModeBackground);

                editor.apply();
                setSummary("settings_complication_night_mode_color");
                setSummary("settings_hour_tick_night_mode_color");
                setSummary("settings_tick_night_mode_color");
                setSummary("settings_minute_text_night_mode_color");
                setSummary("settings_background_night_mode_color");

                Toast.makeText(mContext,
                        getString(R.string.settings_confirmation_night_mode_reset_toast),
                        Toast.LENGTH_SHORT).show();
                break;

            case "donation_1":
            case "donation_3":
            case "donation_5":
            case "donation_10":
                getSettingsActivity().donate(getActivity(), preference.getKey());
                break;

            case "settings_open_changelog":
                Intent openChangelogIntent
                        = new Intent(Intent.ACTION_VIEW)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .setData(Uri.parse("https://github.com/Nxt3/IO_Classic_WatchFace/blob/master/CHANGELOG.md"));

                RemoteIntent.startRemoteActivity(mContext, openChangelogIntent, null);
                Toast.makeText(mContext, getString(R.string.settings_about_opening_toast),
                        Toast.LENGTH_LONG).show();
                break;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    /**
     * Creates an intent to start the ColorPreference Activity
     *
     * @param context      for the intent
     * @param key          for the color preference
     * @param defaultColor for the preference
     * @param reqCode      request code for the intent, used in onActivityResult()
     */
    private void createColorPreferenceActivityIntent(Context context, String key, int defaultColor,
                                                     int reqCode) {
        Intent intent = new Intent(context, ColorActivity.class);
        intent.putExtra("color",
                getPreferenceScreen().getSharedPreferences().getInt(key,
                        defaultColor));
        intent.putExtra("color_names_id", R.array.color_names);
        intent.putExtra("color_values_id", R.array.color_values);
        startActivityForResult(intent, reqCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        final SharedPreferences.Editor editor
                = getPreferenceScreen().getSharedPreferences().edit();

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 0:
                case 1:
                case 2:
                    setComplicationSummary(requestCode,
                            data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO));
                    break;

                case HOUR_TICK_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_hour_tick_color");
                    break;

                case HOUR_TICK_NIGHT_MODE_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_hour_tick_night_mode_color");
                    break;

                case TICK_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_tick_color");
                    break;

                case TICK_NIGHT_MODE_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_tick_night_mode_color");
                    break;

                case MINUTE_TEXT_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_minute_text_color");
                    break;

                case MINUTE_TEXT_NIGHT_MODE_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_minute_text_night_mode_color");
                    break;

                case BACKGROUND_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_background_color");
                    break;

                case BACKGROUND_NIGHT_MODE_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_background_night_mode_color");
                    break;

                case COMPLICATION_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_complication_color");
                    break;

                case COMPLICATION_NIGHT_MODE_COLOR_REQ:
                    handleActivityOnResult(editor, data, "settings_complication_night_mode_color");
                    break;
            }
        }
    }

    /**
     * Handles the onActivityResult for ColorPreference
     *
     * @param editor SharedPrefs editor to store the selected colors
     * @param data   intent to handle the result for
     * @param key    of the preference to handle
     */
    private void handleActivityOnResult(SharedPreferences.Editor editor, Intent data, String key) {
        editor.putString(key,
                data.getStringExtra("color_name"));
        editor.putInt(key.concat("_value"),
                data.getIntExtra("color_value", 0));

        editor.apply();
        setSummary(key);
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProviderInfoRetriever.release();
    }

    /**
     * Gets the SettingsActivity so that we can call donate()
     *
     * @return SettingsActivity
     */
    private SettingsActivity getSettingsActivity() {
        return (SettingsActivity) getActivity();
    }

    /**
     * Gets the list of preferences in a PreferenceScreen
     *
     * @param p    preference to add to the list
     * @param list of preferences in the PreferenceScreen
     * @return a list of all the preferences
     */
    private ArrayList<Preference> getPreferenceList(Preference p, ArrayList<Preference> list) {
        if (p instanceof PreferenceCategory || p instanceof PreferenceScreen) {
            PreferenceGroup prefGroup = (PreferenceGroup) p;

            final int prefCount = prefGroup.getPreferenceCount();

            for (int i = 0; i < prefCount; i++) {
                getPreferenceList(prefGroup.getPreference(i), list);
            }
        }

        if (!(p instanceof PreferenceCategory)) {
            list.add(p);
        }

        return list;
    }

    /**
     * Updates all of the preferences
     */
    private void updateAll() {
        final ArrayList<Preference> preferences
                = getPreferenceList(getPreferenceScreen(), new ArrayList<>());

        for (Preference preference : preferences) {
            final Drawable icon = preference.getIcon();

            if (icon != null) {
                setStyleIcon(preference, icon);
            }

            onSharedPreferenceChanged(getPreferenceScreen().getSharedPreferences(),
                    preference.getKey());
        }
    }

    /**
     * Sets the icon styles for the preferences
     *
     * @param preference belonging to the icon
     * @param icon       to set the styles of
     */
    private void setStyleIcon(Preference preference, Drawable icon) {
        final LayerDrawable layerDrawable
                = (LayerDrawable) mContext.getDrawable(R.drawable.config_icon);
        icon.setTint(Color.WHITE);

        if (layerDrawable != null && layerDrawable.setDrawableByLayerId(R.id.nested_icon, icon)) {
            preference.setIcon(layerDrawable);
        }
    }

    /**
     * Handles what to do when a preference is altered
     *
     * @param sharedPreferences to observe
     * @param key               of the pref that was altered
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        final Preference preference = findPreference(key);

        if (preference != null) {
            final Bundle extras = preference.getExtras();

            if (preference instanceof ListPreference) {
                final String name = extras.getString("icons");

                if (name != null) {
                    final String value = sharedPreferences.getString(key, null);
                    final int id = getResources()
                            .getIdentifier(name, "array", getActivity().getPackageName());

                    final TypedArray icons = getResources().obtainTypedArray(id);
                    final CharSequence[] entryValues
                            = ((ListPreference) preference).getEntryValues();

                    for (int x = 0; x < entryValues.length; x++) {
                        if (value != null && value.equals(entryValues[x])) {
                            setStyleIcon(preference, getResources()
                                    .getDrawable(icons.getResourceId(x, 0)));
                        }
                    }
                    icons.recycle();
                }
            } else if (preference.getSummary() != null && preference.getSummary().equals("%s")) {
                setSummary(key);
            }
        }
    }

    /**
     * Handles setting the summary after an new selection has been made
     *
     * @param key of the setting to update its summary for
     */
    private void setSummary(String key) {
        final Preference preference = findPreference(key);

        if (preference != null) {
            Bundle extras = preference.getExtras();

            final String defaultValue = extras.getString("default");

            final String value = PreferenceManager
                    .getDefaultSharedPreferences(mContext).getString(key, defaultValue);

            preference.setSummary(value);
        }
    }

    /**
     * Sets the summary for the complication selections
     *
     * @param id           of the complication
     * @param providerInfo provider which returns the name of the selected complication in the slot
     */
    private void setComplicationSummary(int id, ComplicationProviderInfo providerInfo) {
        String key;

        switch (id) {
            case 0:
                key = "settings_wallpaper_complication";
                break;
            case 1:
                key = "settings_top_complication";
                break;
            case 2:
                key = "settings_bottom_complication";
                break;
            default:
                return;
        }

        final Preference preference = findPreference(key);

        if (preference != null) {
            final String providerName = (providerInfo != null)
                    ? providerInfo.providerName : getString(R.string.settings_empty);
            preference.setSummary(providerName);
        }
    }

    /**
     * Gets the supported data types for a given complication
     *
     * @param id of the complication
     * @return the array of supported types
     */
    private int[] getSupportedComplicationTypes(int id) {
        switch (id) {
            case 0:
                return Just1MinuteWatchFaceService.COMPLICATION_SUPPORTED_TYPES[1];
            default:
                return Just1MinuteWatchFaceService.COMPLICATION_SUPPORTED_TYPES[0];
        }
    }
}
