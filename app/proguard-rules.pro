# Add project specific ProGuard rules here.
# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
