# Keep XML/manifest entry points through the standard Android optimizer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod

# SharedPreferences retains listeners weakly; do not inline away the owner reference.
-keepclassmembers class com.nexttv.core.data.Prefs {
    android.content.SharedPreferences$OnSharedPreferenceChangeListener motionListener;
}

# JLayer resolves its bundled decoder tables relative to this class's package.
-keep class javazoom.jl.decoder.JavaLayerUtils {
    public static java.io.InputStream getResourceAsStream(java.lang.String);
}
