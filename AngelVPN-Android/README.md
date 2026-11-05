# AngelVPN Android

کلاینت تونل DNS برای اندروید — پشتیبانی از:

| موتور | اسکیما | وضعیت فاز ۱ |
|--------|--------|-------------|
| **SlipNet** | `slipnet://` | پارسر کامل ✅ |
| **DNSTT** | `dnst://` / `dnstt://` | پارسر کامل ✅ |
| **Soar** | `soar://` | پارسر کامل ✅ |
| **Cotten** | `cotten://` | پارسر کامل ✅ |
| **MasterDNS** | `masterdns://` | پارسر کامل ✅ |
| تونل واقعی (VpnService) | — | اسکلت TUN ✅ (موتور هنوز وصل نشده) |

## فازها

1. **فاز ۱ (همین نسخه)** — اسکلت پروژه، پارسر همه لینک‌ها، مدیریت پروفایل، UI فارسی، VpnService با TUN خالی  
2. **فاز ۲** — اتصال موتور DNSTT (gomobile)  
3. **فاز ۳** — Soar / Cotten / MasterDNS / Slipstream  
4. **فاز ۴** — اسکنر DNS، اشتراک، حالت پروکسی، auto-connect  

## ساخت APK

### پیش‌نیاز
- Android Studio Ladybug+ (یا جدیدتر)
- JDK 17
- Android SDK 35 + NDK (برای فازهای بعدی)

### مراحل
```bash
# کلون / کپی پوشه AngelVPN-Android
cd AngelVPN-Android

# با Android Studio باز کنید، یا:
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

اگر `gradlew` ندارید، از Android Studio → Build → Build Bundle(s) / APK(s) → Build APK(s) استفاده کنید.

### نصب روی گوشی
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
روی گوشی: تنظیمات → امنیت → نصب از منابع ناشناس را برای فایل‌منیجر/ADB فعال کنید.

## استفاده (فاز ۱)

1. اپ را باز کنید  
2. «ایمپورت» → لینک کانفیگ را بچسبانید  
3. روی «اتصال» بزنید و مجوز VPN را بدهید  
4. وضعیت «متصل» نمایش داده می‌شود  

> **توجه:** در فاز ۱ ترافیک واقعی از تونل عبور نمی‌کند؛ فقط مسیر مجوز VPN و UI تست می‌شود. موتورهای واقعی در فاز ۲ اضافه می‌شوند.

## ساختار پروژه

```
app/src/main/java/com/angelvpn/app/
├── domain/          # Profile, Engine, ConnState
├── parser/          # ConfigParser (همه اسکیماها)
├── data/            # ProfileStore (DataStore)
├── vpn/             # AngelVpnService + BootReceiver
└── ui/              # Compose UI + Theme
```

## لایسنس

کد این اسکلت برای AngelVPN نوشته شده است.  
موتورهای تونل (dnstt، CottenDNS، …) لایسنس جداگانه دارند و در فازهای بعد با رعایت لایسنس‌شان یکپارچه می‌شوند.
