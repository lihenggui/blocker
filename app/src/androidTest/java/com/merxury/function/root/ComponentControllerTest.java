package com.merxury.function.root;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ComponentControllerTest {
    private static final String TAG = "Test";
    ComponentController controller;
    @Before
    public void setUp() throws Exception {
        controller = ComponentController.getInstance();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void disableComponent() throws Exception {
        boolean isSuccess = controller.disableComponent("com.weico.international", "service.CompleteReceiver1");
        Log.d(TAG, String.valueOf(isSuccess));
    }

    @Test
    public void enableComponent() throws Exception {
        boolean isSuccess = controller.disableComponent("com.weico.international", "service.CompleteReceiver");
        Log.d(TAG, String.valueOf(isSuccess));
    }

}