package com.merxury.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.merxury.adapter.FragmentAdapter;
import com.merxury.blocker.R;
import com.merxury.fragment.AppListFragment;
import com.merxury.service.ShellService;
import com.merxury.utils.BindServiceHelper;
import com.merxury.utils.ContextUtils;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.app_viewpager)
    ViewPager mViewPager;
    @BindView(R.id.app_kind_tabs)
    TabLayout mTabLayout;

    private AlertDialog mAlertDialog;
    private BindServiceHelper mBindServiceHelper;

    public static final String PACKAGE_NAME = "package_name";

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
        PackageManager pm = getPackageManager();
        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(AppListFragment.getInstance(pm, false), getString(R.string.third_party_app));
        adapter.addFragment(AppListFragment.getInstance(pm, true), getString(R.string.system_app));
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
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    private void changeTabBackgroundColor(TabLayout.Tab tab) {
        int colorFrom;
        if (mTabLayout.getBackground() != null) {
            colorFrom = ((ColorDrawable) mTabLayout.getBackground()).getColor();
        } else {
            colorFrom =  ContextCompat.getColor(this, android.R.color.darker_gray);
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
        if (position == 0) return ContextCompat.getColor(this, R.color.md_blue_700);
        else if (position == 1) return ContextCompat.getColor(this, R.color.md_red_700);
        else return ContextCompat.getColor(this, R.color.md_grey_700);
    }

    private void changeColor(int color) {
        mToolbar.setBackgroundColor(color);
        mTabLayout.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);
    }

    private void startShell(final Context context, final String... command) {
        final StringBuilder sb = new StringBuilder();
        mAlertDialog = new AlertDialog.Builder(context)
                .setView(R.layout.dialog_shell)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.send_command, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Activity activity = ContextUtils.getActivity(context);
                        if (activity == null) {
                            return;
                        }

                        ShareCompat.IntentBuilder.from(activity)
                                .setText(sb.toString())
                                .setType("text/plain")
                                .setChooserTitle(R.string.send_command)
                                .startChooser();
                    }
                })
                .show();

        mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        mAlertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);

        final TextView textView = mAlertDialog.findViewById(android.R.id.text1);
        if (textView == null) {
            return;
        }

        textView.setText(R.string.starting_shell);
        mBindServiceHelper.bind(new BindServiceHelper.OnServiceConnectedListener() {
            @Override
            public void onServiceConnected(IBinder binder) {
                ShellService.ShellServiceBinder service = (ShellService.ShellServiceBinder) binder;

                service.run(command, 0, new ShellService.Listener() {
                    @Override
                    public void onFailed() {
                        mBindServiceHelper.unbind();

                        if (mAlertDialog == null) {
                            return;
                        }
                        mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        textView.setText(R.string.cannot_start_no_root);
                    }

                    public void onCommandResult(int commandCode, int exitCode) {
                        mBindServiceHelper.unbind();

                        if (mAlertDialog == null) {
                            return;
                        }

                        mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);

                        if (exitCode != 0) {
                            sb.append('\n').append("Send this to developer may help solve the problem.");
                            mAlertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);

                        }
                    }

                    public void onLine(String line) {
                        if (mAlertDialog == null) {
                            return;
                        }

                        if (sb.length() > 0) {
                            sb.append('\n');
                        }

                        sb.append(line);

                        textView.setText(sb.toString());
                    }
                });
            }
        });
    }

    private void startServer(Context context) {

       // startShell(context, ServerLauncher.COMMAND_ROOT);
    }
}
