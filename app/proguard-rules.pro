-keep class com.wuwaconfig.app.** { *; }
-dontwarn com.wuwaconfig.app.**
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Gson: TypeToken resolves types via runtime generic signatures, which R8 strips
# unless explicitly kept. Without this, release builds fail with
# "TypeToken must be created with a type argument".
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

