package com.merxury.core.nonroot;

import android.content.ComponentName;
import android.content.pm.PackageManager;


import com.merxury.core.IController;

/**
 * Created by lihen on 2018/1/12.
 */

public class NonRootController implements IController{

    private PackageManager mPm;
    private static NonRootController instance;

    private NonRootController(){}
    private NonRootController(PackageManager pm){
        mPm = pm;
    }


    public static NonRootController getInstance(PackageManager pm){
        if(instance == null) {
            synchronized (NonRootController.class) {
                if(instance == null) {
                    instance = new NonRootController(pm);
                }
            }
        }
        return instance;
    }

    @Override
    public boolean switchComponent(String packageName, String componentName, int state) {
        mPm.setComponentEnabledSetting(new ComponentName(packageName, componentName),
                state,
                PackageManager.DONT_KILL_APP);
        return true;
    }
}
