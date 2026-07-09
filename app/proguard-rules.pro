-keep class com.wuwaconfig.app.** { *; }
-dontwarn com.wuwaconfig.app.**
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

