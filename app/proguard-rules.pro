# Gson models — serialized reflectively (BackupStore/ConfigBackup,
# DeployHistoryStore/DeployRecord, ProfileStore/PlayerProfile,
# GachaHistoryStore/GachaData+GachaHistoryEntry, LogAnalysisStore, BattleStatsStore,
# GeneratorOptions, LogEntry). Verified via fromJson/toJson call sites; every
# serialized type lives under com.wuwaconfig.app.model. `{ *; }` also keeps
# FIELD NAMES, which Gson needs since @SerializedName is not used everywhere.
-keep class com.wuwaconfig.app.model.** { *; }

# Instantiated reflectively by the Shizuku host process via ComponentName —
# R8 cannot see this edge, so the whole class must be kept explicitly
# (onTransact/execCommand survive only by fragile intra-class reachability
# if only <init> is kept). It is a Binder, not a Service, so AGP's
# manifest-derived component keep rules do not cover it.
-keep class com.wuwaconfig.app.service.ShellUserService { *; }

# Shizuku uses reflection R8 traces — keep is required, not optional.
# `**` also covers rikka.shizuku.aidl.* (IUserService and friends), which the
# api/aidl artifacts do not ship consumer rules for.
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
# Covers the anonymous `object : TypeToken<T>() {}` subclasses in
# config/GachaApi.kt, config/GachaHistoryStore.kt, config/DeployHistoryStore.kt
# and ui/GachaViewModel.kt — none of which R8 can see being reflected on.
-keep class * extends com.google.gson.reflect.TypeToken

# Gson's @SerializedName-driven deserialization also reads generic type
# information off fields; R8's full mode strips it from non-kept classes.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
