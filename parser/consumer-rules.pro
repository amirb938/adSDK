# XmlPullParser API and implementations
-keep class org.xmlpull.v1.** { *; }
-keep interface org.xmlpull.v1.** { *; }
-keep class org.xmlpull.v1.XmlPullParserFactory { *; }
-keep class * implements org.xmlpull.v1.XmlPullParser { <init>(); }
-keep class * implements org.xmlpull.v1.XmlSerializer { <init>(); }
-dontwarn org.xmlpull.v1.**

# kxml2 implementation
-keep class org.kxml2.** { *; }
-dontwarn org.kxml2.**

# Jackson
-dontwarn com.fasterxml.jackson.databind.ext.NioPathDeserializer