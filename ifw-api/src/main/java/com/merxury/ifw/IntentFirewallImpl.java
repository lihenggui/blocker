package com.merxury.ifw;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.merxury.ifw.entity.Activity;
import com.merxury.ifw.entity.Broadcast;
import com.merxury.ifw.entity.Component;
import com.merxury.ifw.entity.ComponentFilter;
import com.merxury.ifw.entity.ComponentType;
import com.merxury.ifw.entity.Rules;
import com.merxury.ifw.entity.Service;
import com.merxury.libkit.utils.FileUtils;
import com.merxury.libkit.utils.StorageUtils;
import com.stericson.RootTools.RootTools;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IntentFirewallImpl implements IntentFirewall {

    private static final String TAG = "IntentFirewallImpl";
    private static final String EXTENSION = ".xml";
    private static final String FILTER_TEMPLATE = "%s/%s";
    private static IntentFirewallImpl instance;
    private String filename;
    private Rules rules;
    private String tmpPath;
    private String destPath;
    private String packageName;
    private String cacheDir;

    public IntentFirewallImpl(Context context, String packageName) {
        this.packageName = packageName;
        this.filename = packageName + EXTENSION;
        cacheDir = context.getCacheDir().toString() + File.separator;
        tmpPath = cacheDir + filename;
        destPath = StorageUtils.getIfwFolder() + filename;
        openFile();
    }

    public static IntentFirewall getInstance(@NonNull Context context, String packageName) {
        synchronized (IntentFirewallImpl.class) {
            if (instance == null || !instance.getPackageName().equals(packageName)) {
                instance = new IntentFirewallImpl(context, packageName);
            }
        }
        return instance;
    }

    public Rules getRules() {
        return rules;
    }

    @Override
    public void save() throws Exception {
        ensureNoEmptyTag();
        Serializer serializer = new Persister();
        File file = new File(tmpPath);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Cannot delete file: " + tmpPath);
            }
        }
        serializer.write(rules, file);
        FileUtils.cat(tmpPath, destPath);
        FileUtils.chmod(destPath, 644, false);
    }

    @Override
    public boolean add(String packageName, String componentName, ComponentType type) {
        boolean result = false;
        switch (type) {
            case ACTIVITY:
                if (rules.getActivity() == null) {
                    rules.setActivity(new Activity());
                }
                result = addComponentFilter(packageName, componentName, rules.getActivity());
                break;
            case BROADCAST:
                if (rules.getBroadcast() == null) {
                    rules.setBroadcast(new Broadcast());
                }
                result = addComponentFilter(packageName, componentName, rules.getBroadcast());
                break;
            case SERVICE:
                if (rules.getService() == null) {
                    rules.setService(new Service());
                }
                result = addComponentFilter(packageName, componentName, rules.getService());
                break;
            default:
                break;
        }
        return result;
    }

    private boolean addComponentFilter(String packageName, String componentName, Component component) {
        if (component == null) {
            return false;
        }
        List<ComponentFilter> filters = component.getComponentFilters();
        if (filters == null) {
            filters = new ArrayList<>();
            component.setComponentFilters(filters);
        }
        String filterRule = formatName(packageName, componentName);
        //Duplicate filter detection
        for (ComponentFilter filter : filters) {
            if (filter.getName().equals(filterRule)) {
                return false;
            }
        }
        filters.add(new ComponentFilter(filterRule));
        Log.i(TAG, "Added component:" + packageName + "/" + componentName);
        return true;
    }

    private boolean removeComponentFilter(String packageName, String componentName, Component component) {
        if (component == null) {
            return false;
        }
        List<ComponentFilter> filters = component.getComponentFilters();
        if (filters == null) {
            filters = new ArrayList<>();
        }
        String filterRule = formatName(packageName, componentName);
        for (ComponentFilter filter : new ArrayList<>(filters)) {
            if (filterRule.equals(filter.getName())) {
                filters.remove(filter);
            }
        }
        return true;
    }

    @Override
    public boolean remove(String packageName, String componentName, ComponentType type) {
        boolean result = false;
        switch (type) {
            case ACTIVITY:
                result = removeComponentFilter(packageName, componentName, rules.getActivity());
                break;
            case BROADCAST:
                result = removeComponentFilter(packageName, componentName, rules.getBroadcast());
                break;
            case SERVICE:
                result = removeComponentFilter(packageName, componentName, rules.getService());
                break;
            default:
                break;
        }
        return result;
    }

    @Override
    public boolean getComponentEnableState(String packageName, String componentName) {
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
        return getFilterEnableState(packageName, componentName, filters);
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
        String rulePath = StorageUtils.getIfwFolder() + filename;
        Log.d(TAG, "delete file: " + rulePath);
        RootTools.deleteFileOrDirectory(rulePath, false);
    }

    @Override
    public void reload() {
        openFile();
    }

    private boolean getFilterEnableState(String packageName, String componentName, List<ComponentFilter> componentFilters) {
        if (componentFilters == null) {
            return true;
        }
        for (ComponentFilter filter : componentFilters) {
            String filterName = formatName(packageName, componentName);
            if (filterName.equals(filter.getName())) {
                return false;
            }
        }
        return true;
    }

    private void openFile() {
        if (FileUtils.isExist(destPath)) {
            String ruleContent = FileUtils.read(destPath);
            Serializer serializer = new Persister();
            try {
                File file = new File(cacheDir, filename);
                FileWriter writer = new FileWriter(file);
                writer.write(ruleContent);
                writer.close();
                rules = serializer.read(Rules.class, file);
            } catch (Exception e) {
                handleException(e);
                rules = new Rules();
            }
        } else {
            rules = new Rules();
        }
    }

    private void ensureNoEmptyTag() {
        if (rules.getActivity() != null && rules.getActivity().getComponentFilters() != null) {
            if (rules.getActivity().getComponentFilters().size() == 0) {
                rules.setActivity(null);
            }
        }
        if (rules.getBroadcast() != null && rules.getBroadcast().getComponentFilters() != null) {
            if (rules.getBroadcast().getComponentFilters().size() == 0) {
                rules.setBroadcast(null);
            }
        }
        if (rules.getService() != null && rules.getService().getComponentFilters() != null) {
            if (rules.getService().getComponentFilters().size() == 0) {
                rules.setService(null);
            }
        }
    }

    private String formatName(String packageName, String name) {
        return String.format(FILTER_TEMPLATE, packageName, name);
    }

    private void handleException(Exception e) {
        Log.e(TAG, e.getMessage());
        e.printStackTrace();
    }

    @Override
    public String getPackageName() {
        return packageName;
    }
}
