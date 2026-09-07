# Architecture — Moataz Edge Node

## MVP 0.1

الهدف هو إثبات أن هاتف Android يمكنه تشغيل عقدة Telegram شخصية محليًا بدون VPS دائم.

```text
Telegram Bot API
      |
      | getUpdates (long polling)
      v
TelegramNodeService (Android foreground service)
      |
      +--> SQLite durable queue
      |        |
      |        v
      |    PythonWorker (Chaquopy)
      |
      +--> copyMessage --> Telegram destination
```

## طبقات المشروع

- `runtime/`: دورة حياة العقدة، Foreground Service، والحالة الحية.
- `telegram/`: عميل Bot API بدون مكتبات شبكية خارجية.
- `data/`: إعدادات التشغيل وSQLite queue/logs.
- `security/`: تشفير Bot Token باستخدام Android Keystore + AES-GCM.
- `python/`: جسر Kotlin → Python.
- `src/main/python/`: Workers وPlugins المحلية.

## Semantics

الـQueue محلية ودائمة. التحديث يُخزن في SQLite قبل أن ينتقل إلى المعالجة. إعادة الإرسال حاليًا **at-least-once**: في حالة انهيار العملية بعد نجاح `copyMessage` وقبل تسجيل `DONE` قد تحدث نسخة مكررة. سنضيف idempotency/deduplication أقوى لاحقًا.

## لماذا specialUse foreground service؟

العقدة تحتاج اتصال شبكة مستمر يبدأه المستخدم. `dataSync` ليس مناسبًا لعقدة دائمة بسبب حدود Android الحديثة على مدة التشغيل في الخلفية. الخدمة مصنفة `specialUse` مع وصف واضح في Manifest. عند التحضير للنشر عبر Google Play يجب مراجعة سياسة Foreground Services وحالة الاستخدام.

## المرحلة التالية

1. Retry policy مع exponential backoff لكل Job.
2. محرر Rules: source → filter → Python → destination.
3. عدة Bot Tokens وWorkers.
4. استيراد ملفات Python Plugins من Storage Access Framework.
5. مراقبة CPU/RAM/thermal/battery وتطبيق resource profiles.
6. Remote admin commands عبر بوت إداري منفصل.
7. تشغيل AI محلي عبر LiteRT/ONNX عند الحاجة.
