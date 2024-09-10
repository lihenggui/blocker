# Don't obsufcate classes under com.merxury package
# In order to make the error readable, we need to keep the class name
-keep class com.merxury.** { *; }
# Keep file names and line numbers for Crashlytics.
-keepattributes SourceFile,LineNumberTable
