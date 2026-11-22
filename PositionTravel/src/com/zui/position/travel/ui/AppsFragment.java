package com.zui.position.travel.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.zui.position.travel.R;
import com.zui.position.travel.model.AppItem;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.util.TravelIpcHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class AppsFragment extends Fragment {
    private static final int FILTER_USER = 1;
    private static final int FILTER_SELECTED = 2;
    private static final int FILTER_ALL = 3;

    private TravelConfig mConfig;
    private AppAdapter mAdapter;
    private ProgressBar mProgressBar;
    private TextView mTvEmpty;
    private final List<AppItem> mAllApps = new ArrayList<>();
    private final List<AppItem> mDisplayList = new ArrayList<>();
    private final Set<Integer> mSelectedUids = new HashSet<>();

    private int mCurrentFilter = FILTER_USER;
    private String mSearchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_apps, container, false);
        mConfig = new TravelConfig(requireContext());
        mSelectedUids.addAll(mConfig.getTargetUids());

        mProgressBar = view.findViewById(R.id.loading_progress);
        mTvEmpty = view.findViewById(R.id.tv_empty_hint);

        RecyclerView recyclerView = view.findViewById(R.id.rv_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        mAdapter = new AppAdapter();
        recyclerView.setAdapter(mAdapter);

        // 分类 Chip 选择器
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_selected_apps)) {
                mCurrentFilter = FILTER_SELECTED;
            } else if (checkedIds.contains(R.id.chip_all_apps)) {
                mCurrentFilter = FILTER_ALL;
            } else {
                mCurrentFilter = FILTER_USER;
            }
            applyFilter();
        });

        // 搜索输入框
        EditText etSearch = view.findViewById(R.id.et_search_apps);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSearchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadInstalledApps();
        return view;
    }

    private void loadInstalledApps() {
        mProgressBar.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            PackageManager pm = requireContext().getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppItem> list = new ArrayList<>();

            String myPkg = requireContext().getPackageName();
            for (ApplicationInfo info : installed) {
                if (info.packageName.equals(myPkg)) continue;

                boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isUpdatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                boolean isOverlay = info.isResourceOverlay();
                if (isOverlay) continue;

                boolean hasLauncher = pm.getLaunchIntentForPackage(info.packageName) != null;
                boolean isSelected = mSelectedUids.contains(info.uid);

                // 标记是否为纯后台无界面的系统底层组件
                boolean isPureSystem = isSystem && !isUpdatedSystem && !hasLauncher;

                try {
                    String label = info.loadLabel(pm).toString();
                    Drawable icon = info.loadIcon(pm);
                    list.add(new AppItem(label, info.packageName, info.uid, icon, isPureSystem, isSelected));
                } catch (Throwable ignored) {}
            }

            Collections.sort(list);

            new Handler(Looper.getMainLooper()).post(() -> {
                mProgressBar.setVisibility(View.GONE);
                mAllApps.clear();
                mAllApps.addAll(list);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        mDisplayList.clear();

        for (AppItem item : mAllApps) {
            // 1. 分类过滤
            if (mCurrentFilter == FILTER_SELECTED) {
                if (!item.isSelected) continue;
            } else if (mCurrentFilter == FILTER_USER) {
                // 用户应用模式：展示所有第三方应用、桌面可见应用，以及已选中的应用
                if (item.isSystemApp && !item.isSelected) continue;
            }
            // FILTER_ALL 则全量包含

            // 2. 搜索过滤
            if (!mSearchQuery.isEmpty()) {
                boolean matchName = item.appName.toLowerCase().contains(mSearchQuery);
                boolean matchPkg = item.packageName.toLowerCase().contains(mSearchQuery);
                if (!matchName && !matchPkg) continue;
            }

            mDisplayList.add(item);
        }

        mTvEmpty.setVisibility(mDisplayList.isEmpty() ? View.VISIBLE : View.GONE);
        mAdapter.notifyDataSetChanged();
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem item = mDisplayList.get(position);
            holder.tvName.setText(item.appName);
            holder.tvPackage.setText(item.packageName);
            holder.ivIcon.setImageDrawable(item.icon);

            holder.switchTarget.setOnCheckedChangeListener(null);
            holder.switchTarget.setChecked(mSelectedUids.contains(item.uid));

            holder.switchTarget.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.isSelected = isChecked;
                if (isChecked) {
                    mSelectedUids.add(item.uid);
                } else {
                    mSelectedUids.remove(item.uid);
                }
                mConfig.setTargetUids(mSelectedUids);
                TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);

                // 如果当前在“已勾选”模式下取消勾选，刷新列表
                if (mCurrentFilter == FILTER_SELECTED && !isChecked) {
                    applyFilter();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDisplayList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvPackage;
            final MaterialSwitch switchTarget;

            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_app_icon);
                tvName = itemView.findViewById(R.id.tv_app_name);
                tvPackage = itemView.findViewById(R.id.tv_package_name);
                switchTarget = itemView.findViewById(R.id.switch_app_target);
            }
        }
    }
}
