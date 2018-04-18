package com.merxury.ifw;

import android.content.pm.ComponentInfo;

import com.merxury.ifw.entity.ComponentType;
import com.merxury.ifw.entity.Rules;

import java.nio.file.Path;

public class IntentFirewall implements IIntentFirewall {

    private String filePath;
    private Rules rules;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Rules getRules() {
        return rules;
    }

    @Override
    public Rules readRules() {
        return null;
    }

    @Override
    public void saveRules(Path path) {

    }

    @Override
    public void addComponent(ComponentInfo component, ComponentType type) {

    }

    @Override
    public void removeComponent(ComponentInfo component, ComponentType type) {

    }

    @Override
    public void getComponentEnableState(ComponentInfo componentInfo) {

    }
}
