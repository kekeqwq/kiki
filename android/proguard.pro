-keep class io.github.kekeqwq.kiki.HomeActivity {
    public <init>();
}
-keepclassmembers class * {
    public void onClick(android.view.View);
}
-dontwarn **
-allowaccessmodification
-repackageclasses
-overloadaggressively
-dontpreverify
-optimizations !code/simplification/arithmetic,!code/allocation/variable
