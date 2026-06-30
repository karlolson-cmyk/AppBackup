# ── Disable obfuscation (only tree-shake unused code, keep all names) ──
-dontobfuscate
-dontoptimize
-dontpreverify

# ── Keep attributes ──
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ── Appkitz (app code) ──
-keep class com.appkitz.** { *; }

# ── ARSCLib (no consumer rules) ──
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**

# ── OkHttp / OkIO ──
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Error Prone / Tink ──
-dontwarn com.google.errorprone.annotations.**

# ── Kotlin ──
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Enums ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Native methods ──
-keepclasseswithmembernames class * {
    native <methods>;
}
