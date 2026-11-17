# Net Optimizer by Doxaiz — правила ProGuard/R8
# Сейчас минификация выключена (isMinifyEnabled = false), файл нужен для release-сборки.
-keep class com.doxaiz.netoptimizer.core.data.** { *; }
-dontwarn rikka.shizuku.**
