package com.merxury.blocker.core.xml;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by  on 2018/2/1.Mercury
 * read package-restrictions.xml
 */

public class RestrictionFileParser {
    private static final String TAG = "RestrictionFileParser";
    private static final String ns = null;

    public String parse(InputStream in) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

        }catch (XmlPullParserException e) {
            e.printStackTrace();
            Log.e(TAG, "Error in parsing package-restrictions.xml file");
            Log.e(TAG, e.getMessage());
        }catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error in reading package-restrictions.xml file");
            Log.e(TAG, e.getMessage());
        }
        return null;
    }
}
