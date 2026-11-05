-keep class com.angelvpn.app.parser.** { *; }
-keep class com.angelvpn.app.domain.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn org.bouncycastle.**
