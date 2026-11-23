-keep class android.content.pm.** { *; }
-keep class android.app.** { *; }

-keep class com.merxury.blocker.core.controllers.wake.WakeUserService { public <init>(); *; }
-keep class android.os.IUserManager { *; }
-keep class android.os.IUserManager$* { *; }
