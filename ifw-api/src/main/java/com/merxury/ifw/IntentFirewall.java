package com.merxury.ifw;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.util.Log;

import com.merxury.ifw.entity.Activity;
import com.merxury.ifw.entity.Broadcast;
import com.merxury.ifw.entity.Component;
import com.merxury.ifw.entity.ComponentFilter;
import com.merxury.ifw.entity.ComponentType;
import com.merxury.ifw.entity.Rules;
import com.merxury.ifw.entity.Service;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IntentFirewall implements IIntentFirewall {

    private static final String TAG = "IntentFirewallImpl";
    private static final String extension = ".xml";
    private static String filterTemplate = "%s/%s";
    private String filename;
    private Rules rules;
    private String tmpFolder;

    public IntentFirewall(Context context, String filename) {
        this.filename = filename + extension;
        tmpFolder = context.getCacheDir().toString();
        openFile();
    }


    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Rules getRules() {
        return rules;
    }

    @Override
    public File saveRules() {
        Serializer serializer = new Persister();
        File file = new File(tmpFolder + File.separator + filename);
        try {
            serializer.write(rules, file);
        } catch (Exception e) {
            handleException(e);
        }
        return file;
    }

    @Override
    public void addComponent(ComponentInfo component, ComponentType type) {
        switch (type) {
            case ACTIVITY:
                if (rules.getActivity() == null) {
                    rules.setActivity(new Activity());
                }
                addComponentFilter(component, rules.getActivity());
                break;
            case BROADCAST:
                if (rules.getBroadcast() == null) {
                    rules.setBroadcast(new Broadcast());
                }
                addComponentFilter(component, rules.getBroadcast());
                break;
            case SERVICE:
                if (rules.getService() == null) {
                    rules.setService(new Service());
                }
                addComponentFilter(component, rules.getService());
                break;
            default:
                break;
        }
    }

    private void addComponentFilter(ComponentInfo componentInfo, Component component) {
        if (component == null) {
            return;
        }
        List<ComponentFilter> filters = component.getComponentFilters();
        if (filters == null) {
            filters = new ArrayList<>();
            component.setComponentFilters(filters);
        }
        String filterRule = formatName(componentInfo.packageName, componentInfo.name);
        filters.add(new ComponentFilter(filterRule));
    }

    private void removeComponentFilter(ComponentInfo componentInfo, Component component) {
        if (component == null) {
            return;
        }
        List<ComponentFilter> filters = component.getComponentFilters();
        if (filters == null) {
            filters = new ArrayList<>();
        }
        String filterRule = formatName(componentInfo.packageName, componentInfo.name);
        for (ComponentFilter filter : new ArrayList<>(filters)) {
            if (filterRule.equals(filter.getName())) {
                filters.remove(filter);
            }
        }
    }

    @Override
    public void removeComponent(ComponentInfo component, ComponentType type) {
        switch (type) {
            case ACTIVITY:
                removeComponentFilter(component, rules.getActivity());
                break;
            case BROADCAST:
                removeComponentFilter(component, rules.getBroadcast());
                break;
            case SERVICE:
                removeComponentFilter(component, rules.getService());
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getComponentEnableState(ComponentInfo componentInfo) {
        if (rules.getActivity() != null) {
            List<ComponentFilter> componentFilters = rules.getActivity().getComponentFilters();
            if (getFilterEnableState(componentInfo, componentFilters)) {
                return true;
            }
        }
        if (rules.getBroadcast() != null) {
            List<ComponentFilter> componentFilters = rules.getBroadcast().getComponentFilters();
            if (getFilterEnableState(componentInfo, componentFilters)) {
                return true;
            }
        }
        if (rules.getService() != null) {
            List<ComponentFilter> componentFilters = rules.getService().getComponentFilters();
            if (getFilterEnableState(componentInfo, componentFilters)) {
                return true;
            }
        }
        return false;
    }

    private boolean getFilterEnableState(ComponentInfo componentInfo, List<ComponentFilter> componentFilters) {
        if (componentFilters == null) {
            return false;
        }
        for (ComponentFilter filter : componentFilters) {
            String filterName = formatName(componentInfo.packageName, componentInfo.name);
            if (filterName.equals(filter.getName())) {
                return true;
            }
        }
        return false;
    }

    private void openFile() {
        File file = new File(tmpFolder + File.pathSeparator + filename);
        if (file.exists()) {
            Serializer serializer = new Persister();
            try {
                rules = serializer.read(Rules.class, file);
            } catch (Exception e) {
                handleException(e);
            }
        } else {
            rules = new Rules();
        }
    }

    private String formatName(String packageName, String name) {
        return String.format(filterTemplate, packageName, name);
    }

    private void handleException(Exception e) {
        Log.e(TAG, e.getMessage());
        if (BuildConfig.DEBUG) {
            e.printStackTrace();
        }
    }
}
