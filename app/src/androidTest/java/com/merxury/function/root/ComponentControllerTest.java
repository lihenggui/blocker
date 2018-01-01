package com.merxury.function.root;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ComponentControllerTest {
    private static final String TAG = "Test";
    private ComponentController controller;
    @Before
    public void setUp() throws Exception {
        controller = ComponentController.getInstance();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void switchComponent() throws Exception {
        boolean isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver1", false);
        assertEquals(false, isSuccess);
        isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver", false);
        assertEquals(true, isSuccess);
        isSuccess = controller.switchComponent("com.weico.international", "service.CompleteReceiver", true);
        assertEquals(true, isSuccess);
    }


}