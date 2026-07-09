-keep class com.wuwaconfig.app.** { *; }
-dontwarn com.wuwaconfig.app.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

