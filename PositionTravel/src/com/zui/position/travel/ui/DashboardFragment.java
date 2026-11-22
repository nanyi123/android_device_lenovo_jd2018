package com.zui.position.travel.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.zui.position.travel.MainActivity;
import com.zui.position.travel.R;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.service.FloatJoystickService;
import com.zui.position.travel.util.GeocodingHelper;
import com.zui.position.travel.util.TravelIpcHelper;

import java.util.Locale;

public class DashboardFragment extends Fragment {
    private TravelConfig mConfig;
    private MaterialSwitch mSwitchMaster;
    private MaterialSwitch mSwitchJoystick;
    private MaterialSwitch mSwitchBlankWifi;
    private MaterialSwitch mSwitchBlankCell;
    private MaterialSwitch mSwitchFakeCompass;
    private TextView mTvLocationName;
    private TextView mTvCoordinates;
    private TextView mTvAltitude;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        mConfig = new TravelConfig(requireContext());

        mSwitchMaster = view.findViewById(R.id.switch_travel_master);
        mSwitchJoystick = view.findViewById(R.id.switch_joystick);
        mSwitchBlankWifi = view.findViewById(R.id.switch_blank_wifi);
        mSwitchBlankCell = view.findViewById(R.id.switch_blank_cell);
        mSwitchFakeCompass = view.findViewById(R.id.switch_fake_compass);
        mTvLocationName = view.findViewById(R.id.tv_location_name);
        mTvCoordinates = view.findViewById(R.id.tv_coordinates);
        mTvAltitude = view.findViewById(R.id.tv_altitude);

        updateUi();

        mSwitchMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mConfig.setMasterEnabled(isChecked);
            TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
        });

        mSwitchBlankWifi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mConfig.setBlankWifiEnabled(isChecked);
            TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
        });

        mSwitchBlankCell.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mConfig.setBlankCellEnabled(isChecked);
            TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
        });

        if (mSwitchFakeCompass != null) {
            mSwitchFakeCompass.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mConfig.setFakeCompassEnabled(isChecked);
                TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
            });
        }

        mSwitchJoystick.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!Settings.canDrawOverlays(requireContext())) {
                    mSwitchJoystick.setChecked(false);
                    Toast.makeText(requireContext(), R.string.permission_overlay_needed, Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                    return;
                }
                mConfig.setJoystickEnabled(true);
                Intent serviceIntent = new Intent(requireContext(), FloatJoystickService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(serviceIntent);
                } else {
                    requireContext().startService(serviceIntent);
                }
            } else {
                mConfig.setJoystickEnabled(false);
                requireContext().stopService(new Intent(requireContext(), FloatJoystickService.class));
            }
        });

        view.findViewById(R.id.btn_goto_map).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_map);
            }
        });

        mTvAltitude.setOnClickListener(v -> showAltitudeDialog());

        return view;
    }

    private void showAltitudeDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.getDefault(), "%.1f", mConfig.getAltitude()));
        input.setSelection(input.getText().length());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.altitude_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        double alt = Double.parseDouble(input.getText().toString().trim());
                        mConfig.setAltitude(alt);
                        updateUi();
                        TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
    }

    private void updateUi() {
        if (mConfig == null) return;
        mSwitchMaster.setChecked(mConfig.isMasterEnabled());
        mSwitchJoystick.setChecked(mConfig.isJoystickEnabled());
        if (mSwitchBlankWifi != null) {
            mSwitchBlankWifi.setChecked(mConfig.isBlankWifiEnabled());
        }
        if (mSwitchBlankCell != null) {
            mSwitchBlankCell.setChecked(mConfig.isBlankCellEnabled());
        }
        if (mSwitchFakeCompass != null) {
            mSwitchFakeCompass.setChecked(mConfig.isFakeCompassEnabled());
        }
        String locName = mConfig.getLocationName();
        if ("精确坐标输入".equals(locName) || "地图点选位置".equals(locName) || locName.isEmpty()) {
            mTvLocationName.setText("解析地名中...");
            GeocodingHelper.reverseGeocode(mConfig.getLatitude(), mConfig.getLongitude(), placeName -> {
                if (placeName != null && isAdded()) {
                    mConfig.setCoordinates(mConfig.getLatitude(), mConfig.getLongitude(), placeName);
                    mTvLocationName.setText(placeName);
                } else if (isAdded()) {
                    mTvLocationName.setText(String.format(Locale.getDefault(), "位置 (%.4f, %.4f)",
                            mConfig.getLatitude(), mConfig.getLongitude()));
                }
            });
        } else {
            mTvLocationName.setText(locName);
        }
        mTvCoordinates.setText(String.format(Locale.getDefault(), "%.6f, %.6f",
                mConfig.getLatitude(), mConfig.getLongitude()));
        if (mTvAltitude != null) {
            mTvAltitude.setText(String.format(Locale.getDefault(), "海拔高度: %.1f 米 (垂直精度 ±2.5m)",
                    mConfig.getAltitude()));
        }
    }
}
