package com.merxury.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.merxury.blocker.R;
import com.merxury.fragment.AppListFragment;
import com.merxury.ui.adapter.FragmentAdapter;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    public static final String PACKAGE_NAME = "package_name";
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.app_viewpager)
    ViewPager mViewPager;
    @BindView(R.id.app_kind_tabs)
    TabLayout mTabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
            ab.setDisplayHomeAsUpEnabled(true);
        }
        setupDrawer();
        setupViewPager(mViewPager);
        mTabLayout.setupWithViewPager(mViewPager);
        setupTab();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupDrawer() {
        Drawer drawer = new DrawerBuilder().withActivity(this).build();
    }

    private void setupViewPager(ViewPager viewPager) {
        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(AppListFragment.getInstance( false), getString(R.string.third_party_app));
        adapter.addFragment(AppListFragment.getInstance(true), getString(R.string.system_app));
        viewPager.setAdapter(adapter);
    }

    private void setupTab() {
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
            public void onTabReselected(TabLayout.Tab tabr) {

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
        if (position == 0) {
            return ContextCompat.getColor(this, R.color.md_blue_700);
        } else if (position == 1) {
            return ContextCompat.getColor(this, R.color.md_red_700);
        } else {
            return ContextCompat.getColor(this, R.color.md_grey_700);
        }
    }

    private void changeColor(int color) {
        mToolbar.setBackgroundColor(color);
        mTabLayout.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);
    }

}
