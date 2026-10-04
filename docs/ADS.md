# سياسة الإعلانات — AdMob + UMP

> **القاعدة الذهبية:** كل قرار «هل يجوز عرض إعلان هنا؟» يُتخذ في ملف واحد نقي:
> `clock-engine/src/main/kotlin/com/digitalclockpro/clockengine/AdPolicy.kt` —
> بدون أي استيراد من `android.*` أو من SDK الإعلانات، ومُختبَر على الـ JVM في
> `AdPolicyTest.kt`. أي `if` يتعلق بالإعلانات خارج هذا الملف خطأٌ معماري.

---

## ١. المعرّفات — معرّفات الاختبار الرسمية من Google

كلها تعرض **إعلانات اختبار فقط** ولا يمكن أن تُسبب نشاطاً غير مشروع (Invalid Traffic):

| الغرض | المعرّف | المصدر |
|---|---|---|
| App ID (في المانيفست) | `ca-app-pub-3940256099942544~3347511713` | [دليل البدء السريع](https://developers.google.com/admob/android/quick-start) |
| بانر متكيّف مثبّت (Anchored Adaptive) | `ca-app-pub-3940256099942544/9214589741` | [إعلانات الاختبار](https://developers.google.com/admob/android/test-ads) |
| إعلان فتح التطبيق (App Open) | `ca-app-pub-3940256099942544/9257395921` | [دليل App Open](https://developers.google.com/admob/android/app-open) |

- المعرّفات معرّفة كثوابت في `AdPolicy` (`TEST_APP_ID` / `TEST_BANNER_AD_UNIT_ID` /
  `TEST_APP_OPEN_AD_UNIT_ID`)، والـ App ID مكرَّر حرفياً في
  `AndroidManifest.xml` تحت `com.google.android.gms.ads.APPLICATION_ID` — **يجب أن يبقيا متطابقين**.
- `AdPolicyTest` يثبّت القيم الرسمية بالحرف، فيفشل أي اختبار لو استُبدلت بمعرّفات إنتاج بالخطأ.

### قبل النشر على Play Store

1. أنشئ تطبيقاً ووحدات إعلانية في [لوحة AdMob](https://apps.admob.com).
2. استبدل `TEST_APP_ID` في `AdPolicy.kt` **و** القيمة في المانيفست.
3. استبدل `TEST_BANNER_AD_UNIT_ID` و `TEST_APP_OPEN_AD_UNIT_ID`.
4. شغّل `:clock-engine:test` — اختبارات المعرّفات ستفشل عمداً لتذكيرك أنك غيّرتها؛ حدّثها للقيم الإنتاجية.

---

## ٢. الأسطح المحظورة نهائياً (AD_FREE_SURFACES)

| السطح | القيمة في `AdSurface` | السبب |
|---|---|---|
| شاشة رنين المنبّه | `ALARM_RINGING` | مستخدم نصف نائم لا يجوز أن يخطئ لمسة إعلاناً |
| ساعة المكتب | `DESK_CLOCK` | شاشة تُترك ساعات؛ أي إعلان إزعاج صافٍ |
| ساعة الشطرنج | `CHESS_CLOCK` | لعبة جارية وضربات سريعة على نصفي الشاشة |
| محرّر المنبّه | `ALARM_EDITOR` | خطأ لمسة قد يعطّل منبّه الغد |
| دليل OEM | `OEM_GUIDE` | قائمة خطوات حسّاسة لإعدادات البطارية |
| الودجت (+ الاستوديو) | `WIDGET` | أدوات الشاشة الرئيسية وشاشة إعدادها |

في هذه الأسطح الستة: لا بانر (الدالة المجمّعية `AdPolicyBanner` ترجع قبل إنشاء `AdView`
أصلاً)، ولا إعلان فتح تطبيق (`allowsAppOpenAd` يرفضها حتى مع فتح عادي).

الأسطح المسموحة (بانر فقط، أعلى شريط التنقّل): لوحة الساعة `DASHBOARD`، الساعة العالمية
`WORLD_CLOCK`، قائمة المنبّهات `ALARM_LIST`، المؤقّت `TIMER`، ساعة الإيقاف `STOPWATCH`،
الإعدادات `SETTINGS`.

## ٣. إعلان فتح التطبيق لا يظهر أبداً إذا جاء الفتح من منبّه

- `AdLaunchOrigin.ALARM` يُستنتج من `EXTRA_FROM_ALARM` في الـ Intent، وتضبطه:
  `AlarmScheduler.showIntent()` (أيقونة المنبّه في شريط الحالة)، و
  `AlarmRingingActivity` (نية ملء الشاشة نفسها).
- `AdsController.onLaunched(...)` يخزّن الأصل، وأول انتقال إلى المقدّمة يستهلكه ويقرأ
  الحكم من `AdPolicy.allowsAppOpenAd(origin, surface)` — رفض قاطع مهما كان السطح.
- الرفض مزدوج: حتى فتح عادي يُحجب لو كان السطح الظاهر محظوراً (مثلاً العودة للتطبيق
  والمستخدم على محرّر منبّه أو ساعة المكتب).

## ٤. الموافقة (UMP) قبل التهيئة

التسلسل الإلزامي في `AdsController.gatherConsentAndInitialize` (يُستدعى من
`MainActivity.onCreate` فقط — أبداً من `Application` — حتى لا تظهر نافذة موافقة فوق
شاشة رنين منبّه):

1. `UserMessagingPlatform.getConsentInformation(...)`
2. `consentInformation.requestConsentInfoUpdate(...)` — في كل تشغيل.
3. `UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity)` — تعرض النموذج
   فقط إن كان مطلوباً (GDPR/رسائل الخصوصية من لوحة AdMob).
4. `consentInformation.canRequestAds()` ⇒ **عندها فقط** `MobileAds.initialize(...)`
   (مرة واحدة لكل عملية)، وبعدها يبدأ تحميل البانر وإعلان الفتح.

لو رفض المستخدم أو فشل التحديث: تبقى `adsReady = false` ولا يُطلب أي إعلان — التطبيق
يعمل بكامل وظائفه بلا إعلانات.

## ٥. خريطة الأسلاك في طبقة Android

| الملف | دوره |
|---|---|
| `ads/AdsController.kt` | UMP + التهيئة، تتبّع النشاط الحالي وسطحه، تحميل/عرض إعلان الفتح |
| `ads/AdPolicyBanner.kt` | مستضيف البانر الوحيد؛ بوابة `AdPolicy.allowsBanner` |
| `ads/AdLaunchOrigins.kt` | ترجمة الـ Intent إلى `AdLaunchOrigin` (قراءة لا قرار) |
| `presentation/DigitalClockProApp.kt` | يشتق السطح الحالي من المسار/التبويب ويستضيف البانر |
| `presentation/timer/TimerScreen.kt` | يُبلغ عن تبويب الشطرنج (سطح محظور) |
| الأنشطة الثلاثة (الرنين/المكتب/الودجت) | تُبلغ عن سطحها المحظور فور `onCreate` |
| `DigitalClockApp` | `adsController.attach(...)` فقط |

## ٦. الإصدارات

- `com.google.android.gms:play-services-ads:24.0.0` — **آخر سلسلة لا تتطلب Kotlin 2.1**
  (المشروع مثبّت على Kotlin 2.0.20؛ 24.1.0+ يرفع الحد إلى 2.1.0).
- `com.google.android.ump:user-messaging-platform:3.1.0` — نفس الإصدار الذي يُسلّم مع
  `play-services-ads:24.0.0` (متوافق API مع 2.1.0 حتى 4.0.0).
- `androidx.lifecycle:lifecycle-process` — إشارة «التطبيق في المقدّمة» لإعلان الفتح
  (كما يوصي دليل Google لـ App Open).

## ٧. الاختبارات

```bash
./gradlew :clock-engine:test   # 197 + 18 اختباراً إضافياً لـ AdPolicy (المجموع 215)
```

تغط `AdPolicyTest`: الأسطح الستة المحظورة واحداً واحداً، اكتمال مجموعة `AD_FREE_SURFACES`
بالضبط، منع إعلان الفتح من فتح منبّه على أي سطح، منعه فوق الأسطح المحظورة حتى مع فتح
عادي، ومطابقة المعرّفات للقيم الرسمية حرفياً.
