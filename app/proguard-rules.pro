# ── Keep attributes ──
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ── Appkitz ──
-keep class com.appkitz.** { *; }

# ── ARSCLib (no consumer rules, must keep all) ──
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**

# ── OkHttp & OkIO ──
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Error Prone annotations (referenced by Tink/SecurityCrypto) ──
-dontwarn com.google.errorprone.annotations.**

# ── Enums ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
