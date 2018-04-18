package com.merxury.ifw;

import android.content.pm.ComponentInfo;

import com.merxury.ifw.entity.ComponentType;
import com.merxury.ifw.entity.Rules;

import java.nio.file.Path;

public interface IIntentFirewall {
    Rules readRules();

    void saveRules(Path path);

    void addComponent(ComponentInfo component, ComponentType type);

    void removeComponent(ComponentInfo component, ComponentType type);

    void getComponentEnableState(ComponentInfo componentInfo);
}
