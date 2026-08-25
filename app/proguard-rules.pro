# AioShell release 混淆规则（当前 minify 关闭，保留文件即可）

# kotlinx-serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}