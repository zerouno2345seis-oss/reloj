# تطبيق القرآن الكريم لساعة Galaxy Watch 8 Classic
# Quran Companion for Samsung Galaxy Watch 8 Classic

**منصة:** Wear OS 6 + One UI 8 Watch  
**الدقة:** 438×438 (1.34")  
**الذاكرة:** 2GB RAM / 64GB Storage  

---

## ✨ الميزات (Features)

### 1. القرآن الكريم (قراءة فقط – بدون تلاوة)
- نص عثماني حفص كامل (من مصدر مفتوح)
- **بحث صوتي** بالعربي (Speech Recognition)
- **إشارات مرجعية** (اضغط مطولاً على أي آية)
- التحكم بحجم الخط (أ+ / أ-)
- خلفية سوداء + نص أبيض
- أرقام الآيات بالأصفر أو الأخضر (قابل للتبديل)
- شريط تمرير / أزرار للوصول إلى **بداية السورة** أو **نهاية السورة**
- دعم البيزل الدوار للتمرير
- قائمة كاملة بـ 114 سورة

### 2. أوقات الصلاة + تنبيهات
- حساب دقيق offline باستخدام مكتبة **Adhan** (دقة فلكية عالية)
- دعم طرق: رابطة العالم الإسلامي، مصر، أم القرى، كراتشي، ISNA
- تحديد الموقع تلقائياً (GPS)
- عرض الصلاة القادمة + الوقت المتبقي
- تنبيهات اهتزاز + إشعار عند دخول الوقت

### 3. حفظ المواقع الجغرافية
- حفظ **موقع السيارة** بنقرة واحدة
- حفظ مواقع مهمة أو مساجد
- تخزين الإحداثيات محلياً

### 4. ملاحظات صوتية
- تسجيل ملاحظات مهمة بالصوت (MediaRecorder)
- قائمة الملاحظات المحفوظة

---

## 🛠 كيفية البناء والتشغيل (Build & Run)

### المتطلبات
1. **Android Studio** Ladybug أو أحدث (2024.2+)
2. SDK 35 + Wear OS emulator أو جهاز Galaxy Watch 8 Classic
3. JDK 17

### الخطوات
```bash
# 1. افتح المشروع في Android Studio
# File → Open → اختر مجلد QuranWatchApp

# 2. انتظر Gradle Sync

# 3. شغّل على Emulator (Wear OS Round 1.34") أو جهاز حقيقي
# أو عبر ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Gradle Wrapper
إذا لم يكن موجوداً:
```bash
gradle wrapper --gradle-version 8.11.1
```

---

## 📁 هيكل المشروع

```
QuranWatchApp/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── quran_uthmani.min.json   # النص الكامل (1.6 MB)
│   │   ├── java/com/quran/watch8/
│   │   │   ├── data/model/              # Ayah, Bookmark, Location...
│   │   │   ├── data/repository/         # Quran + Preferences (DataStore)
│   │   │   ├── ui/screens/              # Home, Quran, Reader, Prayer...
│   │   │   ├── ui/theme/                # Dark AMOLED theme
│   │   │   ├── ui/viewmodel/
│   │   │   └── util/                    # PrayerTimes, Receivers
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🔑 الأذونات المطلوبة
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` → أوقات الصلاة + حفظ المواقع
- `RECORD_AUDIO` → البحث الصوتي + الملاحظات الصوتية
- `POST_NOTIFICATIONS` + `SCHEDULE_EXACT_ALARM` → تنبيهات الصلاة
- `VIBRATE` → اهتزاز التنبيهات

---

## 🎨 الثيم
- خلفية: `#000000` (أسود نقي لأفضل AMOLED)
- النص: أبيض
- أرقام الآيات: أصفر ذهبي `#FFD700` أو أخضر `#4CAF50`
- تأكيدات: ذهبي `#C9A227`

---

## 📌 ملاحظات مهمة

1. **البيانات**: القرآن محمل offline بالكامل من assets. لا يحتاج إنترنت للقراءة.
2. **البطارية**: تجنب التتبع المستمر للموقع. الحفظ يدوي فقط.
3. **البيزل**: يعمل مع ScalingLazyColumn تلقائياً.
4. **البحث الصوتي**: يعتمد على Google Speech Recognition (يحتاج إنترنت أو نموذج offline إن توفر).
5. **التطوير المستقبلي**:
   - تشغيل الملاحظات الصوتية (MediaPlayer)
   - بوصلة للعودة إلى موقع السيارة
   - complications لساعة الوجه (أوقات الصلاة)
   - دعم صفحات المصحف (604 صفحة)
   - خطوط عثمانية مخصصة (KFGQPC)

---

## 📜 الترخيص والمصادر
- نص القرآن: Uthmani Hafs (مفتوح المصدر – fawazahmed0/quran-api & Quran Complex)
- حساب الأوقات: [Adhan Kotlin](https://github.com/batoulapps/adhan-kotlin) (MIT)
- التطبيق نفسه: يمكنك استخدامه بحرية لأغراض شخصية أو تطويره

---

**تم بناؤه خصيصاً لـ Galaxy Watch 8 Classic (2025/2026)**  
بسم الله الرحمن الرحيم

---

## 🇦🇷 دعم الأرجنتين / Buenos Aires (تحديث)

### أوقات الصلاة
- **الافتراضي**: بوينس آيرس (CABA) −34.6037, −58.3816
- **طريقة الحساب الافتراضية**: **ISNA** (الأكثر استخداماً في الأرجنتين)
- **منطقة زمنية**: America/Argentina/Buenos_Aires (UTC−3)
- **محافظة بوينس آيرس**:
  - بوينس آيرس (العاصمة)
  - لا بلاتا (La Plata)
  - مار دل بلاتا (Mar del Plata)
  - باهيا بلانكا
  - تيغري، كيلمس، لوماس دي زامورا
- مدن أخرى: قرطبة، روزاريو، مندوزا
- إمكانية تغيير الموقع يدوياً أو عبر GPS
- إذا رُفض إذن الموقع → يُستخدم بوينس آيرس تلقائياً

### الأذونات عند فتح التطبيق
عند الدخول لأول مرة يطلب التطبيق تلقائياً:
1. 📍 **الموقع** (FINE + COARSE) → أوقات الصلاة + حفظ المواقع
2. 🎤 **الميكروفون** → البحث الصوتي + الملاحظات الصوتية
3. 🔔 **الإشعارات** (Android 13+) → تنبيهات الصلاة

إذا رُفضت الأذونات، يعمل التطبيق بالوضع الافتراضي (بوينس آيرس + بدون تسجيل صوت).


---

## 🔄 دعم الإطار الدوار (Rotating Bezel) – Galaxy Watch 8 Classic

تم تفعيل الدعم الكامل للإطار الدوار المادي في كل الشاشات:

| الشاشة | سلوك الإطار |
|--------|-------------|
| الرئيسية | تمرير القائمة |
| قائمة السور | تمرير السور + نتائج البحث |
| **قارئ القرآن** | تمرير الآيات بسلاسة + وضع خاص لتغيير حجم الخط بالإطار |
| أوقات الصلاة | تمرير الأوقات + قائمة المواقع |
| المواقع / الملاحظات / الإشارات / الإعدادات | تمرير كامل |

### كيفية الاستخدام في قارئ القرآن
1. **حرّك الإطار** → تمرير الآيات (طبيعي)
2. اضغط زر **Aa** في الشريط السفلي → يتحول الإطار إلى **تغيير حجم الخط**
3. اضغط **Aa** مرة أخرى للعودة للتمرير
4. **PositionIndicator** (شريط التمرير الجانبي) يتحدث مع الإطار
5. دبل تاب لإخفاء/إظهار الأدوات

### التقنيات المستخدمة
- `Modifier.onRotaryScrollEvent`
- `FocusRequester` + `focusable()` (ضروري لاستقبال أحداث الإطار)
- `ScalingLazyListState.scrollBy` / `animateScrollToItem`
- يعمل مع البيزل المادي (Physical Bezel) والـ RSB (Rotating Side Button)


---

## 📦 بناء الـ APK وتثبيته (مهم)

### إذا لم يكن لديك Android Studio (موصى به):
1. حمّل **Android Studio** مجاناً من: https://developer.android.com/studio
2. ثبّته وافتحه
3. File → Open → اختر مجلد `QuranWatchApp`
4. انتظر Gradle Sync (قد يطلب تحميل SDK تلقائياً)
5. Build → Build Bundle(s) / APK(s) → **Build APK(s)**
6. بعد الانتهاء سيظهر رابط "locate" — انسخ ملف `app-debug.apk`
7. ثبّته على الساعة باستخدام **Wear Installer 2** أو **GeminiMan** أو ADB

### أوامر سريعة (إذا كان لديك Android Studio / SDK):
```bash
cd QuranWatchApp
./gradlew assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk
```

### تثبيت على الساعة:
- فعّل Wireless debugging على الساعة
- استخدم تطبيق Wear Installer 2 على الهاتف
- أو: `adb install -r app-debug.apk`

