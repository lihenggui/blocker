package com.merxury.core.nonroot;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Created by lihen on 2018/1/13.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class NonRootControllerTest {
    private Context context;
    private PackageManager mPm;
    private NonRootController controller;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getContext();
        mPm = context.getPackageManager();
        controller = NonRootController.getInstance(mPm);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void switchComponent() throws Exception {
        String packageName = "com.weico.international";
        String receiver = "com.sina.push.receiver.PushSDKReceiver";
        controller.switchComponent(packageName, receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

}