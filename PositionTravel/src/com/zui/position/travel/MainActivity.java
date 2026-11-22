package com.zui.position.travel;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.zui.position.travel.ui.AppsFragment;
import com.zui.position.travel.ui.DashboardFragment;
import com.zui.position.travel.ui.MapFragment;
import com.zui.position.travel.ui.RunFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView mBottomNav;
    private Fragment mDashboardFragment;
    private Fragment mRunFragment;
    private Fragment mMapFragment;
    private Fragment mAppsFragment;
    private Fragment mActiveFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        setContentView(R.layout.activity_main);

        // 避让刘海屏 (DisplayCutout) 与状态栏，确保顶部 AppBar 不被刘海遮挡
        View root = findViewById(R.id.main_root);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                // 顶部避让刘海与状态栏高度，左右避让凹口/圆角
                v.setPadding(insets.left, insets.top, insets.right, 0);
                return windowInsets;
            });
        }

        mBottomNav = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            mDashboardFragment = new DashboardFragment();
            mRunFragment = new RunFragment();
            mMapFragment = new MapFragment();
            mAppsFragment = new AppsFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, mAppsFragment, "apps").hide(mAppsFragment)
                    .add(R.id.fragment_container, mMapFragment, "map").hide(mMapFragment)
                    .add(R.id.fragment_container, mRunFragment, "run").hide(mRunFragment)
                    .add(R.id.fragment_container, mDashboardFragment, "dashboard")
                    .commit();
            mActiveFragment = mDashboardFragment;
        } else {
            mDashboardFragment = getSupportFragmentManager().findFragmentByTag("dashboard");
            mRunFragment = getSupportFragmentManager().findFragmentByTag("run");
            mMapFragment = getSupportFragmentManager().findFragmentByTag("map");
            mAppsFragment = getSupportFragmentManager().findFragmentByTag("apps");
            if (mDashboardFragment == null) mDashboardFragment = new DashboardFragment();
            if (mRunFragment == null) mRunFragment = new RunFragment();
            if (mMapFragment == null) mMapFragment = new MapFragment();
            if (mAppsFragment == null) mAppsFragment = new AppsFragment();

            int selectedId = mBottomNav.getSelectedItemId();
            if (selectedId == R.id.nav_run) {
                mActiveFragment = mRunFragment;
            } else if (selectedId == R.id.nav_map) {
                mActiveFragment = mMapFragment;
            } else if (selectedId == R.id.nav_apps) {
                mActiveFragment = mAppsFragment;
            } else {
                mActiveFragment = mDashboardFragment;
            }
        }

        mBottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                switchFragment(mDashboardFragment);
                return true;
            } else if (id == R.id.nav_run) {
                switchFragment(mRunFragment);
                return true;
            } else if (id == R.id.nav_map) {
                switchFragment(mMapFragment);
                return true;
            } else if (id == R.id.nav_apps) {
                switchFragment(mAppsFragment);
                return true;
            }
            return false;
        });

        handleIntent(getIntent(), savedInstanceState);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, null);
    }

    private void handleIntent(android.content.Intent intent, Bundle savedInstanceState) {
        if (intent != null && intent.hasExtra("tab")) {
            String tabName = intent.getStringExtra("tab");
            if ("map".equals(tabName)) {
                selectTab(R.id.nav_map);
                return;
            } else if ("run".equals(tabName)) {
                selectTab(R.id.nav_run);
                return;
            } else if ("apps".equals(tabName)) {
                selectTab(R.id.nav_apps);
                return;
            } else if ("dashboard".equals(tabName)) {
                selectTab(R.id.nav_dashboard);
                return;
            }
        }
        if (savedInstanceState == null && mBottomNav != null) {
            selectTab(R.id.nav_dashboard);
        }
    }

    public void selectTab(int navId) {
        if (mBottomNav != null) {
            mBottomNav.setSelectedItemId(navId);
        }
    }

    private void switchFragment(Fragment target) {
        if (mActiveFragment == target) return;
        getSupportFragmentManager().beginTransaction()
                .hide(mActiveFragment)
                .show(target)
                .commit();
        mActiveFragment = target;
    }
}
