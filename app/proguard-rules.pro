# Currently release minification is disabled. Preserve the WebView bridge if enabled later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
# Firebase/Coil/Coroutines ship consumer rules; validate a minified build before enabling R8.
