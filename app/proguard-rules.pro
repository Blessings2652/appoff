# ==============================================================================
# Shizuku Protection & Keep Rules
# Ensure Shizuku IPC, AIDL binders, and reflection targets are preserved completely
# ==============================================================================
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.binder.** { *; }
-keep class rikka.hidden.** { *; }
-keep class rikka.core.** { *; }
-keep class rikka.preference.** { *; }
-keep class rikka.sui.** { *; }

-keep interface rikka.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }

-keepclassmembers class ** implements rikka.shizuku.Shizuku$* { *; }

# Preserve AIDL / Binder IPC generated classes and interfaces
-keep class * implements android.os.IInterface { *; }
-keep class * extends android.os.Binder { *; }

# Keep App's Shizuku integration & IPC wrappers
-keep class com.theblacksheep.appoff.shizuku.** { *; }
-keepclassmembers class com.theblacksheep.appoff.shizuku.** { *; }

# Don't warn on optional Shizuku/Rikka classes
-dontwarn rikka.**
-dontwarn moe.shizuku.**
-dontwarn dev.rikka.shizuku.**

# ==============================================================================
# Native JNI Methods (C++ native-lib.cpp interop)
# ==============================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.theblacksheep.appoff.core.NativeMemoryUtils { *; }

# ==============================================================================
# Android Core Components & Application Class Safety Net
# ==============================================================================
-keep class com.theblacksheep.appoff.CleanerApp { *; }
-keep public class * extends android.app.Application { *; }
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }
-keep public class * extends androidx.work.Worker { *; }
-keep public class * extends androidx.work.ListenableWorker { *; }

# ==============================================================================
# Dagger 2 Dependency Injection Keep Rules
# ==============================================================================
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **_Provide*Factory { *; }
-keep class com.theblacksheep.appoff.di.** { *; }
-dontwarn com.google.errorprone.annotations.**

# ==============================================================================
# Kotlin Coroutines & Attributes
# ==============================================================================
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable
-keepclassmembers class * {
    @kotlin.jvm.Volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
