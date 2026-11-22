package com.zui.position.travel.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zui.position.travel.R;
import com.zui.position.travel.engine.UnifiedRunPhysicsEngine;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.service.RunService;

import java.util.Locale;

public class RunFragment extends Fragment implements RunService.RunStateListener {

    private TravelConfig mConfig;
    private double mSelectedTargetKm = 2.40;
    private double mSelectedSpeedMps = 2.50; // 默认慢跑 6'40" (2.5 m/s)

    private Chip mChipStatus;
    private TextView mTvDistance;
    private TextView mTvTargetProgress;
    private LinearProgressIndicator mProgressBar;
    private TextView mTvTime;
    private TextView mTvPace;
    private TextView mTvLaps;
    private TextView mTvSteps;
    private TextView mTvTrackOrigin;

    private ChipGroup mCgDistance;
    private ChipGroup mCgPace;
    private Chip mChipDist20;
    private Chip mChipDist24;
    private Chip mChipDist30;
    private Chip mChipDist40;
    private Chip mChipDistCustom;

    private LinearLayout mLayoutIdleActions;
    private LinearLayout mLayoutRunningActions;
    private MaterialButton mBtnStart;
    private MaterialButton mBtnPauseResume;
    private MaterialButton mBtnStop;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_run, container, false);
        mConfig = new TravelConfig(requireContext());

        mChipStatus = root.findViewById(R.id.chip_run_status);
        mTvDistance = root.findViewById(R.id.tv_run_distance);
        mTvTargetProgress = root.findViewById(R.id.tv_run_target_progress);
        mProgressBar = root.findViewById(R.id.progress_run_bar);
        mTvTime = root.findViewById(R.id.tv_stat_time);
        mTvPace = root.findViewById(R.id.tv_stat_pace);
        mTvLaps = root.findViewById(R.id.tv_stat_laps);
        mTvSteps = root.findViewById(R.id.tv_stat_steps);
        mTvTrackOrigin = root.findViewById(R.id.tv_track_origin_desc);

        mCgDistance = root.findViewById(R.id.cg_target_distance);
        mCgPace = root.findViewById(R.id.cg_target_pace);
        mChipDist20 = root.findViewById(R.id.chip_dist_2_0);
        mChipDist24 = root.findViewById(R.id.chip_dist_2_4);
        mChipDist30 = root.findViewById(R.id.chip_dist_3_0);
        mChipDist40 = root.findViewById(R.id.chip_dist_4_0);
        mChipDistCustom = root.findViewById(R.id.chip_dist_custom);

        mLayoutIdleActions = root.findViewById(R.id.layout_idle_actions);
        mLayoutRunningActions = root.findViewById(R.id.layout_running_actions);
        mBtnStart = root.findViewById(R.id.btn_start_run);
        mBtnPauseResume = root.findViewById(R.id.btn_pause_resume);
        mBtnStop = root.findViewById(R.id.btn_stop_run);

        setupDistanceChips();
        setupPaceChips();
        setupActionButtons();

        return root;
    }

    private void setupDistanceChips() {
        mCgDistance.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_dist_2_0) {
                mSelectedTargetKm = 2.0;
            } else if (id == R.id.chip_dist_2_4) {
                mSelectedTargetKm = 2.4;
            } else if (id == R.id.chip_dist_3_0) {
                mSelectedTargetKm = 3.0;
            } else if (id == R.id.chip_dist_4_0) {
                mSelectedTargetKm = 4.0;
            } else if (id == R.id.chip_dist_custom) {
                showCustomDistanceDialog();
            }
            updateTargetDisplay(RunService.getCurrentDistance());
        });
    }

    private void showCustomDistanceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.run_custom_dialog_title);

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(R.string.run_custom_dialog_hint);
        input.setText(String.format(Locale.getDefault(), "%.1f", mSelectedTargetKm));
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            try {
                double val = Double.parseDouble(input.getText().toString().trim());
                if (val > 0.1 && val <= 50.0) {
                    mSelectedTargetKm = val;
                    mChipDistCustom.setText(String.format(Locale.getDefault(), "%.1f km", val));
                    updateTargetDisplay(RunService.getCurrentDistance());
                } else {
                    Toast.makeText(requireContext(), "请输入有效距离 (0.2 ~ 50 km)", Toast.LENGTH_SHORT).show();
                    mChipDist24.setChecked(true);
                }
            } catch (Exception e) {
                mChipDist24.setChecked(true);
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> {
            mChipDist24.setChecked(true);
            dialog.cancel();
        });
        builder.show();
    }

    private void setupPaceChips() {
        mCgPace.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_pace_jog) {
                mSelectedSpeedMps = 2.50; // 6'40"
            } else if (id == R.id.chip_pace_medium) {
                mSelectedSpeedMps = 3.00; // 5'33"
            } else if (id == R.id.chip_pace_fast) {
                mSelectedSpeedMps = 3.50; // 4'45"
            }
            if (!RunService.isServiceRunning()) {
                updatePaceDisplay(mSelectedSpeedMps);
            }
        });
    }

    private void setupActionButtons() {
        mBtnStart.setOnClickListener(v -> {
            // 确保穿越总开关处于开启状态
            if (!mConfig.isMasterEnabled()) {
                mConfig.setMasterEnabled(true);
            }

            Intent intent = new Intent(requireContext(), RunService.class);
            intent.setAction(RunService.ACTION_START);
            intent.putExtra(RunService.EXTRA_TARGET_KM, mSelectedTargetKm);
            intent.putExtra(RunService.EXTRA_PACE_SPEED, mSelectedSpeedMps);

            ContextCompat.startForegroundService(requireContext(), intent);
            setRunningUiState(true, false);
        });

        mBtnPauseResume.setOnClickListener(v -> {
            if (RunService.isServicePaused()) {
                Intent intent = new Intent(requireContext(), RunService.class);
                intent.setAction(RunService.ACTION_RESUME);
                requireContext().startService(intent);
                setRunningUiState(true, false);
            } else {
                Intent intent = new Intent(requireContext(), RunService.class);
                intent.setAction(RunService.ACTION_PAUSE);
                requireContext().startService(intent);
                setRunningUiState(true, true);
            }
        });

        mBtnStop.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RunService.class);
            intent.setAction(RunService.ACTION_STOP);
            requireContext().startService(intent);
            setRunningUiState(false, false);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        RunService.setListener(this);
        refreshUiState();
    }

    @Override
    public void onPause() {
        super.onPause();
        RunService.setListener(null);
    }

    private void refreshUiState() {
        boolean isRunning = RunService.isServiceRunning();
        boolean isPaused = RunService.isServicePaused();
        setRunningUiState(isRunning, isPaused);

        double dist = RunService.getCurrentDistance();
        double targetDist = RunService.getTargetDistance();
        if (targetDist > 0) {
            mSelectedTargetKm = targetDist / 1000.0;
        }
        updateTargetDisplay(dist);

        long elapsedMs = RunService.getElapsedTimeMs();
        updateTimeDisplay(elapsedMs);

        int laps = RunService.getLaps();
        int totalLaps = (int) Math.ceil((mSelectedTargetKm * 1000.0) / UnifiedRunPhysicsEngine.TRACK_LAP_LEN);
        mTvLaps.setText(String.format(Locale.getDefault(), "%d / %d 圈", laps, totalLaps));

        int steps = (int) (dist / UnifiedRunPhysicsEngine.DEFAULT_STRIDE_LEN);
        mTvSteps.setText(String.format(Locale.getDefault(), "%,d 步", steps));

        updatePaceDisplay(mSelectedSpeedMps);

        if (mTvTrackOrigin != null && mConfig != null) {
            mTvTrackOrigin.setText(String.format(Locale.getDefault(),
                    "操场中心起点: %s (%.6f, %.6f)",
                    mConfig.getLocationName(), mConfig.getLatitude(), mConfig.getLongitude()));
        }
    }

    private void setRunningUiState(boolean isRunning, boolean isPaused) {
        if (!isAdded()) return;

        if (isRunning) {
            mLayoutIdleActions.setVisibility(View.GONE);
            mLayoutRunningActions.setVisibility(View.VISIBLE);
            mCgDistance.setEnabled(false);
            mCgPace.setEnabled(false);

            if (isPaused) {
                mChipStatus.setText(R.string.run_status_paused);
                mBtnPauseResume.setText(R.string.run_btn_resume);
                mBtnPauseResume.setIconResource(android.R.drawable.ic_media_play);
            } else {
                mChipStatus.setText(R.string.run_status_running);
                mBtnPauseResume.setText(R.string.run_btn_pause);
                mBtnPauseResume.setIconResource(android.R.drawable.ic_media_pause);
            }
        } else {
            mLayoutIdleActions.setVisibility(View.VISIBLE);
            mLayoutRunningActions.setVisibility(View.GONE);
            mCgDistance.setEnabled(true);
            mCgPace.setEnabled(true);
            mChipStatus.setText(R.string.run_status_idle);
        }
    }

    private void updateTargetDisplay(double distMeters) {
        if (!isAdded()) return;
        double distKm = distMeters / 1000.0;
        mTvDistance.setText(String.format(Locale.getDefault(), "%.2f", distKm));

        int percent = (int) Math.min(100, Math.max(0, (distKm / mSelectedTargetKm) * 100));
        mProgressBar.setProgress(percent);
        mTvTargetProgress.setText(String.format(Locale.getDefault(), "目标 %.2f km (%d%%)", mSelectedTargetKm, percent));
    }

    private void updateTimeDisplay(long elapsedMs) {
        if (!isAdded()) return;
        long secs = elapsedMs / 1000;
        long mins = secs / 60;
        long remSecs = secs % 60;
        mTvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, remSecs));
    }

    private void updatePaceDisplay(double speedMps) {
        if (!isAdded()) return;
        int paceSecPerKm = speedMps > 0.5 ? (int) (1000.0 / speedMps) : 400;
        mTvPace.setText(String.format(Locale.getDefault(), "%d'%02d\"", paceSecPerKm / 60, paceSecPerKm % 60));
    }

    @Override
    public void onRunFrame(UnifiedRunPhysicsEngine.PhysicsFrame frame, double totalDist, double targetDist,
                           long elapsedMs, boolean isPaused) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            updateTargetDisplay(totalDist);
            updateTimeDisplay(elapsedMs);

            int laps = (int) (totalDist / UnifiedRunPhysicsEngine.TRACK_LAP_LEN);
            int totalLaps = (int) Math.ceil(targetDist / UnifiedRunPhysicsEngine.TRACK_LAP_LEN);
            mTvLaps.setText(String.format(Locale.getDefault(), "%d / %d 圈", laps + 1, totalLaps));

            mTvSteps.setText(String.format(Locale.getDefault(), "%,d 步", frame.stepCount));
            updatePaceDisplay(frame.currentSpeed);
        });
    }

    @Override
    public void onRunStateChanged(boolean isRunning, boolean isPaused) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> setRunningUiState(isRunning, isPaused));
    }

    @Override
    public void onRunStopped() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            setRunningUiState(false, false);
            refreshUiState();
        });
    }
}
