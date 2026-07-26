# AGENT ProGuard Rules

# Keep data classes for Gson
-keepclassmembers class com.agent.telegram.** { *; }
-keepclassmembers class com.agent.ai.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep service names
-keep class com.agent.service.** { *; }

# Keep Application class
-keep class com.agent.AgentApp { *; }
