-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep,includedescriptorclasses class com.llamatik.** { *; }

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers enum com.emberr.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

-keepclassmembers class com.emberr.**$$serializer {
    *;
}

-keep class com.google.crypto.tink.proto.** { *; }
-keep class * implements com.google.crypto.tink.KeyManager { *; }
-dontwarn com.google.crypto.tink.**

-dontwarn com.google.errorprone.annotations.**

-keepnames class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

-keep class androidx.work.InputMerger {
    <init>();
}

-keep class * extends androidx.work.InputMerger {
    <init>();
}
