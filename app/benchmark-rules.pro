# Instrumentation reads runtime frame/recomposition metrics in this optimized variant.
-keep class androidx.tracing.** { *; }
-keep class androidx.compose.runtime.Recomposer { public *; }
-keep class androidx.compose.runtime.Recomposer$Companion { public *; }
-keep interface androidx.compose.runtime.RecomposerInfo { *; }
-keep,allowoptimization class kotlin.** { *; }
-keep,allowoptimization class kotlinx.coroutines.** { *; }
