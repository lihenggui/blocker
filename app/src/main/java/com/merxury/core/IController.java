package com.merxury.core;

/**
 * Created by Mercury on 2018/1/13.
 * An Interface that defines what controller should do
 */

public interface IController {

    /**
     * a method to change a component's state
     *
     * @param packageName   package name
     * @param componentName component name
     * @param state         PackageManager.COMPONENT_ENABLED_STATE_ENABLED: enable component
     *                      COMPONENT_ENABLED_STATE_DISABLED: disable component
     * @return true : changed component state successfully
     * false: cannot disable component
     */
    boolean switchComponent(String packageName, String componentName, int state);
}
