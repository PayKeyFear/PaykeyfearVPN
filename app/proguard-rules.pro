# ---------- kotlinx.serialization ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.paykeyfear.vpn.**$$serializer { *; }
-keepclassmembers class com.paykeyfear.vpn.** {
    *** Companion;
}
-keepclasseswithmembers class com.paykeyfear.vpn.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,allowobfuscation,allowshrinking class com.paykeyfear.vpn.core.model.** { *; }

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ---------- Room ----------
-keep class androidx.room.** { *; }
-keep @androidx.room.* class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
# Generated *_Impl classes
-keep class **_Impl { *; }

# ---------- Navigation / Compose ----------
-keep class androidx.navigation.** { *; }
-dontwarn androidx.compose.**

# ---------- Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.debug.**

# ---------- DataStore ----------
-keep class androidx.datastore.** { *; }

# ---------- Timber ----------
-keep class timber.log.** { *; }
-dontwarn org.jetbrains.annotations.**

# ---------- JNI / native ----------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------- gomobile umbrella native backend ----------
# The umbrella .aar (paykeyfearnative) is referenced exclusively through
# reflection (AwgNative / VlessNative / Hysteria2Native / NativeBackendVersions
# all use Class.forName). Without explicit keeps R8 strips the whole
# package, which also strips go.Seq's static initializer — the one that
# calls System.loadLibrary("gojni"). In that state tunneling silently
# degrades to "tun up, no packets" and disconnect can't release the fd.
-keep class paykeyfearnative.** { *; }
-keep interface paykeyfearnative.** { *; }
-keep class go.** { *; }
-keep interface go.** { *; }
-keep class mobile.** { *; }
-dontwarn go.**
-dontwarn paykeyfearnative.**
-dontwarn mobile.**

# ---------- App entry points (manifest-referenced) ----------
-keep class com.paykeyfear.vpn.PaykeyfearApp { *; }
-keep class com.paykeyfear.vpn.MainActivity { *; }
-keep class com.paykeyfear.vpn.service.PaykeyfearVpnService { *; }
-keep class com.paykeyfear.vpn.boot.BootReceiver { *; }

# ---------- Compose reflection helpers ----------
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
