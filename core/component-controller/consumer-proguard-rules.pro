-keep class android.content.pm.** { *; }
-keep class android.app.** { *; }

-keep class com.merxury.blocker.core.controllers.wake.WakeUserService { public <init>(); *; }
# Shizuku loads its user service by name and looks up both constructors by reflection.
-keep class com.merxury.blocker.core.privileged.shizuku.ShizukuPrivilegedService { public <init>(...); }
# The service finds each call's CREATOR by reflection.
-keepclassmembers class * implements com.merxury.blocker.core.privileged.PrivilegedCall { public static final ** CREATOR; }
-keep class android.os.IUserManager { *; }
-keep class android.os.IUserManager$* { *; }
