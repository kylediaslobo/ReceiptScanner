# kotlinx.serialization: keep generated serializers for @Serializable models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.kyled.receiptscanner.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
