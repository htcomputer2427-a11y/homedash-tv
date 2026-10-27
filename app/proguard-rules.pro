-dontobfuscate
-keep class vn.homedash.tv.MediaListener
# player.html calls these by name.
-keepclassmembers class vn.homedash.tv.WebPlayer$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
