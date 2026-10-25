# Library consumer rules protect their own JNI and reflection entry points.
# Preserve generic information used by QuickJS typed bindings and Kotlin reflection.
-keepattributes Signature,InnerClasses,EnclosingMethod

# ONNX Runtime's JNI looks up tensor metadata, exceptions and constructors by
# their Java names. Its Android AAR does not supply consumer keep rules.
-keep class ai.onnxruntime.** { *; }

# AMap ships a JAR with JNI/reflection entry points and no AAR consumer rules.
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }

# The optional soft-RTK positioning extension is absent from AMap's base JAR.
# We only display the map; positioning uses Android, and SDK location is disabled.
# Keep this exception exact so other missing runtime dependencies still fail R8.
-dontwarn com.amap.ams.gnss.GnssSoftLocator


# Glance(:widget) 引入的 WorkManager 通过反射实例化 Room 生成类 WorkDatabase_Impl，
# R8 full mode 会裁掉其无参构造导致冷启动崩溃（NoSuchMethodException / Failed to
# create an instance）。work 2.10 的 consumer 规则在本工具链下未覆盖到，显式保留。
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
