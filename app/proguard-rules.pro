# Only matters once isMinifyEnabled is turned on: the API models are
# serialized by Gson through their field names, which shrinking would rename.
-keep class com.kiteplus.kite.mobile.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
