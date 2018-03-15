package com.merxury.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.merxury.adapter.FragmentAdapter;
import com.merxury.blocker.R;
import com.merxury.constant.Constant;
import com.merxury.core.ApplicationComponents;
import com.merxury.entity.Application;
import com.merxury.fragment.ComponentFragment;
import com.merxury.utils.ApplicationUtils;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

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
    @BindView(R.id.app_info_layout)
    ConstraintLayout mAppBriefLayout;
    @BindView(R.id.app_info_app_name)
    TextView mAppName;
    @BindView(R.id.app_info_icon)
    ImageView mAppIcon;
    @BindView(R.id.app_info_app_package_name)
    TextView mAppPackageName;

    private Application mApplicationInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_component);
        ButterKnife.bind(this);
        Intent intent = getIntent();
        mApplicationInfo = intent.getParcelableExtra(Constant.APPLICATION);
        initActionBar();
        initAppBriefInfoLayout(this);
        initDrawer();
        initViewPager();
        initTab();
        initOnClickListener();
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

    private void initDrawer() {
        Drawer drawer = new DrawerBuilder().withActivity(this).build();
    }

    private void initViewPager() {
        PackageManager pm = getPackageManager();
        String packageName = mApplicationInfo.getPackageName();
        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(ComponentFragment.getInstance(pm, packageName, ComponentFragment.RECEIVER), getString(R.string.receiver));
        adapter.addFragment(ComponentFragment.getInstance(pm, packageName, ComponentFragment.SERVICE), getString(R.string.service));
        adapter.addFragment(ComponentFragment.getInstance(pm, packageName, ComponentFragment.ACTIVITY), getString(R.string.activity));
        adapter.addFragment(ComponentFragment.getInstance(pm, packageName, ComponentFragment.PROVIDER), getString(R.string.provider));
        mViewPager.setAdapter(adapter);
    }

    private void initTab() {
        mTabLayout.setupWithViewPager(mViewPager);
        changeColor(getColorForTab(0));
        mTabLayout.setSelectedTabIndicatorColor(getResources().getColor(R.color.md_white_1000));
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                changeTabBackgroundColor(tab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    private void changeTabBackgroundColor(TabLayout.Tab tab) {
        int colorFrom;
        if (mTabLayout.getBackground() != null) {
            colorFrom = ((ColorDrawable) mTabLayout.getBackground()).getColor();
        } else {
            colorFrom = ContextCompat.getColor(this, android.R.color.darker_gray);
        }
        int colorTo = getColorForTab(tab.getPosition());
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int color = (int) animation.getAnimatedValue();
                changeColor(color);
            }
        });
        colorAnimation.setDuration(500);
        colorAnimation.start();
    }

    private int getColorForTab(int position) {
        switch (position) {
            case 0:
                return ContextCompat.getColor(this, R.color.md_blue_700);
            case 1:
                return ContextCompat.getColor(this, R.color.md_light_green_700);
            case 2:
                return ContextCompat.getColor(this, R.color.md_orange_700);
            case 3:
                return ContextCompat.getColor(this, R.color.md_red_700);
            default:
                return ContextCompat.getColor(this, R.color.md_grey_700);
        }
    }

    private void changeColor(int color) {
        mToolbar.setBackgroundColor(color);
        mTabLayout.setBackgroundColor(color);
        mCollapsingToolbarLayout.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);
    }

    private void initActionBar() {
        setSupportActionBar(mToolbar);
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowTitleEnabled(false);
        }
    }

    @SuppressLint("CheckResult")
    private void initAppBriefInfoLayout(Context context) {
        final PackageManager pm = getPackageManager();
        Single.create((SingleOnSubscribe<PackageInfo>) emitter -> {
            PackageInfo info = ApplicationComponents.getApplicationComponents(pm, mApplicationInfo.getPackageName());
            emitter.onSuccess(info);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    Glide.with(context)
                            .load(info.applicationInfo.loadIcon(pm))
                            .into(mAppIcon);
                    mAppName.setText(info.applicationInfo.loadLabel(pm));
                    mAppPackageName.setText(info.packageName);
                }, throwable -> {
                    //TODO Error handling
                });
    }

    private void initOnClickListener() {
        mAppIcon.setOnClickListener((view) -> ApplicationUtils.Companion.startApplication(this, mApplicationInfo.getPackageName()));
    }
}
