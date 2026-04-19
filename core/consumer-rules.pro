# Keep serializers for core models
-keep,allowobfuscation,allowshrinking class com.paykeyfear.vpn.core.model.** { *; }
-keepclassmembers class com.paykeyfear.vpn.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
