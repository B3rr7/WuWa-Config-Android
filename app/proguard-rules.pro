# Gson models — serialized reflectively (BackupStore, DeployHistoryStore,
# ProfileStore, GachaHistoryStore, LogAnalysisStore, BattleStatsStore,
# GeneratorOptions, GachaData). Verified via fromJson/toJson call sites.
-keep class com.wuwaconfig.app.model.** { *; }

# Instantiated reflectively by the Shizuku host process via ComponentName —
# R8 cannot see this edge, so the whole class must be kept explicitly
# (onTransact/execCommand survive only by fragile intra-class reachability
# if only <init> is kept).
-keep class com.wuwaconfig.app.service.ShellUserService { *; }

# Shizuku uses reflection R8 traces — keep is required, not optional.
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
