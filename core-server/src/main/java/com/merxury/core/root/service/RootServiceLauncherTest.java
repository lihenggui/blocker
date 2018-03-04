package com.merxury.core.root.service;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Wiki on 2018/2/4.
 */
public class RootServiceLauncherTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getContext();
    }

    @Test
    public void startService() throws Exception {
        boolean result = RootServiceLauncher.startService(context);
        assertEquals(result, true);
    }

}