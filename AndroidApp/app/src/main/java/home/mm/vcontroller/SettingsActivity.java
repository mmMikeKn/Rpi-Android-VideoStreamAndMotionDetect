package home.mm.vcontroller;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends PreferenceActivity {
    private static int surfaceTextureWidth = 1920, surfaceTextureHeight = 1080;

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                Log.v("-----------------", "ListPreference stringValue:" + stringValue);
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        Intent intent = getIntent();
        int v = intent.getIntExtra("width", -1);
        surfaceTextureWidth = v < 0 ? surfaceTextureWidth : v;
        v = intent.getIntExtra("height", -1);
        surfaceTextureHeight = v < 0 ? surfaceTextureHeight : v;
    }


    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (!super.onMenuItemSelected(featureId, item)) {
                NavUtils.navigateUpFromSameTask(this);
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public boolean onIsMultiPane() {
        return (this.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || NetworkPreferenceFragment.class.getName().equals(fragmentName)
                || CameraPreferenceFragment.class.getName().equals(fragmentName)
                || MotionPreferenceFragment.class.getName().equals(fragmentName)
                || ExternalSpiPreferenceFragment.class.getName().equals(fragmentName);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NetworkPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_network);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_connectTimeout)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_tcpIpPort)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_internalHost)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_externalHost)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_password)));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class CameraPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_camera);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_cam_fps)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_cam_quality)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_cam_bitrate)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_vISO)));
            //--------------
            ListPreference preference = (ListPreference) findPreference(getString(R.string.cfg_cam_vDivider));
            List<String> values = new ArrayList<>();
            List<String> dividers = new ArrayList<>();
            for (double i = 5; i >= 0; i -= 0.5) {
                int h = (int) (surfaceTextureHeight / i);
                if (h >= 480 && h <= 1080) {
                    dividers.add(Double.toString(i));
                    values.add(Integer.toString((int) (surfaceTextureWidth / i)) + "x" + Integer.toString(h));
                }
            }
            preference.setEntries(values.toArray(new String[values.size()]));
            preference.setEntryValues(dividers.toArray(new String[dividers.size()]));
            bindPreferenceSummaryToValue(preference);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class MotionPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_motion);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionSnapshotFileMask)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionCheckPollingDt)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionThresholdValue)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionThresholdCnt)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionThresholdSAD)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_modeSnapshot)));

            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionSnapshotDt)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionDetectTimeWindow)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_motionNumberInTimeWindow)));

        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ExternalSpiPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_external_spi);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(getString(R.string.cfg_externalControllerBatteryVoltageAlarmLevel)));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
