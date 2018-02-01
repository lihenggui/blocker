package com.merxury.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import com.merxury.adapter.FragmentAdapter;
import com.merxury.blocker.R;
import com.merxury.fragment.AppListFragment;
import com.merxury.fragment.ComponentFragment;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ComponentActivity extends AppCompatActivity {

    @BindView(R.id.component_toolbar)
    Toolbar mToolbar;
    @BindView(R.id.component_appbar)
    AppBarLayout mAppBar;
    @BindView(R.id.component_collapsing_toolbar)
    CollapsingToolbarLayout mCollapsingToolbarLayout;
    @BindView(R.id.component_tabs)
    TabLayout mTabLayout;
    @BindView(R.id.component_viewpager)
    ViewPager mViewPager;

    private String mPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_component);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
            ab.setDisplayHomeAsUpEnabled(true);
        }
        Intent intent = getIntent();
        mPackageName = intent.getStringExtra(MainActivity.PACKAGE_NAME);
        setupDrawer();
        setupViewPager(mViewPager);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void setupDrawer() {
        Drawer drawer = new DrawerBuilder().withActivity(this).build();
    }

    private void setupViewPager(ViewPager viewPager) {
        PackageManager pm = getPackageManager();
        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(ComponentFragment.getInstance(pm, mPackageName, ComponentFragment.RECEIVER), "Receiver");
        adapter.addFragment(ComponentFragment.getInstance(pm, mPackageName, ComponentFragment.SERVICE), "Service");
        adapter.addFragment(ComponentFragment.getInstance(pm, mPackageName, ComponentFragment.ACTIVITY), "Activity");
        adapter.addFragment(ComponentFragment.getInstance(pm, mPackageName, ComponentFragment.PROVIDER), "Provider");
        viewPager.setAdapter(adapter);
    }
}
