package com.merxury.ifw;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.support.annotation.NonNull;
import android.util.Log;

import com.merxury.ifw.entity.Activity;
import com.merxury.ifw.entity.Broadcast;
import com.merxury.ifw.entity.Component;
import com.merxury.ifw.entity.ComponentFilter;
import com.merxury.ifw.entity.ComponentType;
import com.merxury.ifw.entity.Rules;
import com.merxury.ifw.entity.Service;
import com.merxury.ifw.util.PermissionUtils;
import com.merxury.ifw.util.StorageUtils;
import com.stericson.RootTools.RootTools;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IntentFirewallImpl implements IntentFirewall {

    private static final String TAG = "IntentFirewallImpl";
    private static final String EXTENSION = ".xml";
    private static final String IFW_FOLDER = "/ifw/";
    private static final String FILTER_TEMPLATE = "%s/%s";
    private String filename;
    private Rules rules;
    private String tmpPath;
    private String destPath;

    public IntentFirewallImpl(Context context, String packageName) {
        this.filename = packageName + EXTENSION;
        tmpPath = context.getCacheDir().toString() + File.separator + filename;
        destPath = getIfwRulePath();
        openFile();
    }

    public Rules getRules() {
        return rules;
    }

    @Override
    public void save() throws Exception {
        Serializer serializer = new Persister();
        File file = new File(tmpPath);
        serializer.write(rules, file);
        PermissionUtils.setPermission(tmpPath, 644);
        RootTools.copyFile(tmpPath, destPath, false, true);
    }

    @Override
    public boolean add(ComponentInfo component, ComponentType type) {
        boolean result = false;
        switch (type) {
            case ACTIVITY:
                if (rules.getActivity() == null) {
                    rules.setActivity(new Activity());
                }
                result = addComponentFilter(component, rules.getActivity());
                break;
            case BROADCAST:
                if (rules.getBroadcast() == null) {
                    rules.setBroadcast(new Broadcast());
                }
                result = addComponentFilter(component, rules.getBroadcast());
                break;
            case SERVICE:
                if (rules.getService() == null) {
                    rules.setService(new Service());
                }
                result = addComponentFilter(component, rules.getService());
                break;
            default:
                break;
        }
        if (result) {
            try {
                save();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
                return false;
            }
        }
        return result;
    }

    private boolean addComponentFilter(ComponentInfo componentInfo, Component component) {
        if (component == null) {
            return false;
        }
        List<ComponentFilter> filters = component.getComponentFilters();
        if (filters == null) {
            filters = new ArrayList<>();
            component.setComponentFilters(filters);
        }
        String filterRule = formatName(componentInfo.packageName, componentInfo.name);
        //Duplicate filter detection
        for (ComponentFilter filter : filters) {
            if (filter.getName().equals(filterRule)) {
                return false;
            }
        }
        filters.add(new ComponentFilter(filterRule));
        return true;
    }

    private boolean removeComponentFilter(ComponentInfo componentInfo, Component component) {
        if (component == null) {
            return false;
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
        return true;
    }

    @Override
    public boolean remove(ComponentInfo component, ComponentType type) {
        boolean result = false;
        switch (type) {
            case ACTIVITY:
                result = removeComponentFilter(component, rules.getActivity());
                break;
            case BROADCAST:
                result = removeComponentFilter(component, rules.getBroadcast());
                break;
            case SERVICE:
                result = removeComponentFilter(component, rules.getService());
                break;
            default:
                break;
        }
        if (result) {
            try {
                save();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
                return false;
            }
        }
        return result;
    }

    @Override
    public boolean getComponentEnableState(ComponentInfo componentInfo) {
        List<ComponentFilter> filters = new ArrayList<>();
        if (rules.getActivity() != null) {
            filters.addAll(rules.getActivity().getComponentFilters());
        }
        if (rules.getBroadcast() != null) {
            filters.addAll(rules.getBroadcast().getComponentFilters());
        }
        if (rules.getService() != null) {
            filters.addAll(rules.getService().getComponentFilters());
        }
        return getFilterEnableState(componentInfo, filters);
    }

    @Override
    public void clear() {
        clear(filename);
    }

    @Override
    public void clear(String name) {
        if (name == null) {
            return;
        }
        String rulePath = StorageUtils.getSystemSecureDirectory() + IFW_FOLDER + filename;
        Log.d(TAG, "delete file: " + rulePath);
        RootTools.deleteFileOrDirectory(rulePath, false);
    }

    private boolean getFilterEnableState(ComponentInfo componentInfo, List<ComponentFilter> componentFilters) {
        if (componentFilters == null) {
            return true;
        }
        for (ComponentFilter filter : componentFilters) {
            String filterName = formatName(componentInfo.packageName, componentInfo.name);
            if (filterName.equals(filter.getName())) {
                return false;
            }
        }
        return true;
    }

    private void openFile() {
        File destFile = new File(destPath);
        if (destFile.exists()) {
            RootTools.copyFile(destPath, tmpPath, false, true);
            File tmpFile = new File(tmpPath);
            Serializer serializer = new Persister();
            try {
                rules = serializer.read(Rules.class, tmpFile);
            } catch (Exception e) {
                handleException(e);
                rules = new Rules();
            }
        } else {
            rules = new Rules();
        }
    }

    private String formatName(String packageName, String name) {
        return String.format(FILTER_TEMPLATE, packageName, name);
    }

    @NonNull
    private String getIfwRulePath() {
        return StorageUtils.getSystemSecureDirectory().getPath() + IFW_FOLDER + filename;
    }

    private void handleException(Exception e) {
        Log.e(TAG, e.getMessage());
        if (BuildConfig.DEBUG) {
            e.printStackTrace();
        }
    }
}
