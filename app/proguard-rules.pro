# R8 rules for the release build (full mode).
#
# Most libraries here ship their own consumer rules, so this file stays small:
#   - Room, Hilt/Dagger, WorkManager, OkHttp and Retrofit bundle keep rules.
#   - Compose needs nothing extra.
# Only what R8 cannot infer for this app belongs below.

# kotlinx.serialization resolves serializers reflectively at runtime from the
# generated $$serializer companion, which R8 cannot see being used. Without
# this, catalog responses fail to parse only in release builds.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# The DTOs themselves: their field names are the JSON wire format.
-keep,includedescriptorclasses class org.veraproject.veravideo.data.remote.**  { *; }

# Retrofit interfaces are implemented by a runtime proxy driven by their
# annotations and generic return types.
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# The YouTube IFrame player runs inside a WebView and calls back into the
# library through a JavaScript bridge, which R8 cannot trace.
-keepclassmembers class com.pierfrancescosoffritti.androidyoutubeplayer.** {
    @android.webkit.JavascriptInterface <methods>;
}
