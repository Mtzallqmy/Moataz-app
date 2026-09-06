# Moataz Edge Node

**Moataz Edge Node** هو تطبيق Android شخصي يحوّل الهاتف إلى عقدة تشغيل محلية لبوتات Telegram ومهام Python والأتمتة، بهدف تقليل الاعتماد على VPS دائم للمهام الشخصية قليلة ومتوسطة الحمل.

## حالة المشروع

MVP `0.1.0` قيد التطوير. الأساس الحالي يشمل:

- Telegram Bot API عبر Long Polling (`getUpdates`).
- Foreground Service يبدأه المستخدم لتشغيل العقدة في الخلفية.
- Queue محلية دائمة باستخدام SQLite.
- إعادة تمرير الرسائل من Source chat إلى Destination chat باستخدام `copyMessage`.
- Python 3.13 داخل التطبيق عبر Chaquopy.
- Hook بايثون يعمل على كل Telegram update قبل الإرسال.
- تشفير Bot Token باستخدام Android Keystore وAES-GCM.
- Logs محلية ولوحة تحكم أولية بـJetpack Compose.

## Stack

- Android / Kotlin
- Jetpack Compose 1.11 (BOM 2026.04.01)
- Android Gradle Plugin 9.2.1
- compileSdk 36 / targetSdk 36 / minSdk 24
- Chaquopy 17.0 + Python 3.13
- SQLite
- Telegram Bot API

## التشغيل

1. افتح المشروع في Android Studio حديث.
2. استخدم JDK 17 وGradle 9.4.1.
3. تأكد من تثبيت Android SDK Platform 36 وBuild Tools 36.0.0.
4. شغّل التطبيق على جهاز `arm64-v8a` أو محاكي `x86_64`.
5. أنشئ Bot من BotFather وأضف Token داخل التطبيق.
6. أضف البوت إلى المجموعة/القناة بالصلاحيات اللازمة.
7. أدخل Source chat ID وDestination chat ID ثم اضغط **حفظ وتشغيل**.

> إذا تُرك Source فارغًا فستتم معالجة جميع الرسائل التي تصل للبوت. وإذا تُرك Destination فارغًا فسيعمل Python hook فقط بدون نسخ الرسالة.

## Python Worker

أول Worker موجود هنا:

`app/src/main/python/worker.py`

والدالة الحالية:

```python
def process_update(raw_update: str) -> str:
    ...
```

ستتحول لاحقًا إلى Plugin API يعيد Actions مثل filter / transform / route / AI.

## الأمان

Bot Token لا يُحفظ كنص عادي. التطبيق ينشئ مفتاح AES داخل Android Keystore ويستخدم AES-GCM لتشفير الأسرار قبل تخزينها في SharedPreferences.

## ملاحظات Android

التشغيل المستمر مصنف Foreground Service من نوع `specialUse` لأن العقدة حالة أتمتة شخصية مستمرة لا تناسب `dataSync`. قبل أي نشر عام على Google Play يجب مراجعة متطلبات وسياسات Foreground Service الخاصة بهذا الاستخدام.

## المعمارية

راجع [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## خارطة الطريق

- Rules Engine بصيغة source → filter → Python → destination.
- عدة Bots وعدة Workers.
- جدولة Cron/Jobs.
- Resource Manager للـCPU/RAM/حرارة الجهاز والبطارية.
- Plugin manager لملفات Python.
- Remote admin bot.
- Local AI runtime.
- Phone-to-phone distributed nodes.
