package com.merxury.blocker.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.merxury.blocker.core.root.RootController;
import com.merxury.libkit.entity.Application;
import com.merxury.libkit.utils.ApplicationUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RootControllerTest {
    private static final String TAG = "Test";
    private RootController controller;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getContext();
        controller = new RootController(context);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void switchComponent() {
        boolean isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver1", PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertEquals(false, isSuccess);
        isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver", PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertEquals(true, isSuccess);
        isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver", PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertEquals(true, isSuccess);
    }

    @Test
    public void performanceTest() {
        long time1, time2;
        time1 = System.currentTimeMillis();
        PackageManager pm = context.getPackageManager();
        List<Application> info = ApplicationUtil.getApplicationList(pm);
        time2 = System.currentTimeMillis();
        Log.d(TAG, "get applitation list takes " + (time2 - time1) + "milliseconds");

    }


}