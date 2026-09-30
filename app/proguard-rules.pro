# Gson models — serialized reflectively (BackupStore/ConfigBackup,
# DeployHistoryStore/DeployRecord, ProfileStore/PlayerProfile,
# GachaHistoryStore/GachaData+GachaHistoryEntry, LogAnalysisStore, BattleStatsStore,
# GeneratorOptions, LogEntry). Verified via fromJson/toJson call sites; every
# serialized type lives under com.wuwaconfig.app.model.
#
# Only FIELD NAMES have to survive: every deserialization target is a concrete
# class literal (PlayerProfile::class.java) or an object : TypeToken<T>, both of
# which R8 rewrites when it renames the class. There is no polymorphic type
# discriminator in any stored JSON, so class names are never resolved from a
# string and do not need keeping. And Gson allocates through Unsafe, so it needs
# no constructor, no shrink protection and no optimisation protection.
#
# This rule used to be `-keep class com.wuwaconfig.app.model.** { *; }`, which
# held 40 classes, 369 fields and 708 methods out of shrinking, obfuscation AND
# optimisation. R8's keep-radius analyzer measured it at 1,117 exclusively-kept
# items - the single largest rule in the build - for what is a field-name
# requirement. The { *; } members below are named explicitly rather than
# wildcarded for the same reason: they are the ones Gson actually reflects on.
-keepclassmembers,allowoptimization,allowshrinking class com.wuwaconfig.app.model.** {
    <fields>;
}

# Instantiated reflectively by the Shizuku host process via ComponentName —
# R8 cannot see this edge, so the class and its no-arg constructor must be kept
# explicitly. onTransact is reached virtually from that constructor, so it needs
# no rule of its own; it used to be covered only by the { *; } below, which also
# pinned TRANSACTION_EXEC_COMMAND, MAX_BINDER_OUTPUT and readBounded.
# It is a Binder, not a Service, so AGP's manifest-derived component keep rules
# do not cover it.
-keep class com.wuwaconfig.app.service.ShellUserService {
    <init>();
}

# Shizuku's api artifact ships an EMPTY consumer proguard.txt, so nothing below
# is redundant — but the two halves need different things and used to share one
# over-broad rule (274 exclusively-kept items).
#
# moe.shizuku.** is the AIDL layer. Its Stub classes carry a DESCRIPTOR string
# that is the interface token both peers enforce against, and TRANSACTION_*
# constants that are matched by number. Obfuscating any of that breaks the
# handshake at runtime, so these stay fully kept.
-keep class moe.shizuku.** { *; }
# rikka.shizuku.** is the client API. The host resolves ShizukuProvider through
# the manifest, so class names need to stay stable, but nothing reflects into
# these members, so optimisation can be allowed.
-keep,allowoptimization class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Gson: TypeToken resolves types via runtime generic signatures, which R8 strips
# unless explicitly kept. Without this, release builds fail with
# "TypeToken must be created with a type argument".
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# NOTE: gson 2.13.2's own consumer rules (gson.pro) already ship both of the
# following, so they are no longer repeated here:
#   -keep,allowobfuscation class com.google.gson.reflect.TypeToken
#   -keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken
#   -keepclassmembers,allowobfuscation class * { @SerializedName <fields>; }
# The analyzer measured our copies at zero exclusively-kept items, i.e. they
# were pure duplication. Kept here as a note because their absence looks like a
# regression and someone will otherwise re-add them.
