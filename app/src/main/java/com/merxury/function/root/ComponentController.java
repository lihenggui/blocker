package com.merxury.function.root;

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

public class ComponentController {
    private static final String DISABLE_COMPONENT_TEMPLATE = "pm disable %s/.%s";
    private static final String ENABLE_COMPONENT_TEMPLATE = "pm enable %s/.%s";

    public static boolean disableComponent(String packageName, String componentName) {
        String comm = String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName);
        return false;
    }
}
