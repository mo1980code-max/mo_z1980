-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.digitalclockpro.widget.** { *; }
-keep class com.digitalclockpro.alarm.** { *; }
-keepclassmembers class * extends android.appwidget.AppWidgetProvider { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }
-keepnames class kotlinx.serialization.** { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; *** serializer(...); }

# --- Ads (Google Mobile Ads + UMP) --------------------------------------------
# play-services-ads and user-messaging-platform ship their own consumer ProGuard
# rules inside their AARs, so the classes this app touches directly (AdView, AdSize,
# AdRequest, AppOpenAd, MobileAds, FullScreenContentCallback, UserMessagingPlatform,
# ConsentInformation, ConsentRequestParameters) need no extra keeps here. Add
# ad-related keeps below ONLY if a mediated network adapter requires them.
