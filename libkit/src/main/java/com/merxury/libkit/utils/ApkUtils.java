package com.merxury.libkit.utils;

import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ApkUtils {
    private static String TAG = "APKUtils";

    /**
     * Parses AndroidManifest of the given apkFile and returns the value of
     * minSdkVersion using undocumented API which is marked as
     * "not to be used by applications"
     * Source: https://stackoverflow.com/questions/20372193/get-minsdkversion-and-targetsdkversion-from-apk-file
     *
     * @param apkFile
     * @return minSdkVersion or -1 if not found in Manifest
     */
    public static int getMinSdkVersion(File apkFile) {
        try {
            XmlResourceParser parser = getParserForManifest(apkFile);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG
                        && parser.getName().equals("uses-sdk")) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).equals("minSdkVersion")) {
                            return parser.getAttributeIntValue(i, -1);
                        }
                    }
                }
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        return -1;
    }

    /**
     * Tries to get the parser for the given apkFile from {@link AssetManager}
     * using undocumented API which is marked as
     * "not to be used by applications"
     *
     * @param apkFile
     * @return
     * @throws IOException
     */
    private static XmlResourceParser getParserForManifest(final File apkFile)
            throws IOException {
        final Object assetManagerInstance = getAssetManager();
        final int cookie = addAssets(apkFile, assetManagerInstance);
        return ((AssetManager) assetManagerInstance).openXmlResourceParser(
                cookie, "AndroidManifest.xml");
    }

    /**
     * Get the cookie of an asset using an undocumented API call that is marked
     * as "no to be used by applications" in its source code
     *
     * @return the cookie
     * @see <a
     * href="http://androidxref.com/5.1.1_r6/xref/frameworks/base/core/java/android/content/res/AssetManager.java#612">AssetManager.java#612</a>
     */
    private static int addAssets(final File apkFile,
                                 final Object assetManagerInstance) {
        try {
            Method addAssetPath = assetManagerInstance.getClass().getMethod(
                    "addAssetPath", String.class);
            return (Integer) addAssetPath.invoke(assetManagerInstance,
                    apkFile.getAbsolutePath());
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Get {@link AssetManager} using reflection
     *
     * @return
     */
    private static Object getAssetManager() {
        Class assetManagerClass;
        try {
            assetManagerClass = Class
                    .forName("android.content.res.AssetManager");
            return assetManagerClass.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
