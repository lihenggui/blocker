# The hidden-API stubs under src/main/java/android/ stand in for framework classes at compile time.
# At run time the framework's own classes win, so each stub must keep its exact name and members:
# a renamed or stripped stub would leave Blocker's subclasses, such as the uninstall status receiver
# that extends IIntentSender.Stub, and its calls bound to a class the framework does not have. One
# rule for the whole namespace keeps stubs added later too.
-keep class android.** { *; }

-keep class com.merxury.blocker.core.controllers.wake.WakeUserService { public <init>(); *; }
# Shizuku loads its user service by name and looks up both constructors by reflection.
-keep class com.merxury.blocker.core.privileged.shizuku.ShizukuPrivilegedService { public <init>(...); }
# The service finds each call's CREATOR by reflection.
-keepclassmembers class * implements com.merxury.blocker.core.privileged.PrivilegedCall { public static final ** CREATOR; }
