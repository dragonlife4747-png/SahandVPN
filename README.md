# سهند وی‌پی‌ان (Sahand VPN)

کلاینت ساده و فارسی اندروید برای اتصال به سرورهای V2Ray/Xray — با یک صفحه، دو دکمه.

بر پایهٔ [v2rayNG](https://github.com/2dust/v2rayNG) ساخته شده و تحت لایسنس GPL-3.0 منتشر می‌شود.

## ویژگی‌ها

- **دو دکمه، بدون پیچیدگی** — «یافتن نقطه اتصال» و «اتصال»؛ لیست کانفیگ‌ها پنهان است.
- **انتخاب خودکار بهترین نقطه** — ابتدا نقاط سالم قبلی دوباره آزمایش می‌شوند، سپس جست‌وجوی گسترده.
- **تأیید واقعی اتصال** — وضعیت «متصل» فقط وقتی نمایش داده می‌شود که ترافیک واقعاً از تونل عبور کند.
- **بازیابی خودکار** — پس از قطعی یا جابه‌جایی بین وای‌فای و دیتای موبایل، نقطهٔ جایگزین انتخاب می‌شود.
- **رابط فارسی** — فونت وزیرمتن، اعداد فارسی و پشتیبانی از راست‌به‌چپ.
- **بدون تبلیغ، بدون ردیابی.**

## نصب

آخرین نسخهٔ APK را از بخش [Releases](https://github.com/dragonlife4747-png/SahandVPN/releases) دانلود کنید.

| معماری | فایل |
|---|---|
| اکثر گوشی‌های امروزی | `SahandVPN_*_arm64-v8a.apk` |
| گوشی‌های قدیمی‌تر | `SahandVPN_*_armeabi-v7a.apk` |
| نامشخص | `SahandVPN_*_universal.apk` (حجم بیشتر) |

پس از دانلود، نصب از منابع ناشناس را برای مرورگر یا فایل‌منیجر فعال کنید.

## ساخت از سورس

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd V2rayNG
./gradlew :app:assemblePlaystoreRelease --console=plain
```

خروجی در `V2rayNG/app/build/outputs/apk/playstore/release/`.

برای امضای نسخهٔ انتشار، فایل `V2rayNG/keystore.properties` را با مقادیر خود بسازید (این فایل در مخزن قرار نمی‌گیرد):

```properties
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## ساختار پروژه

```
V2rayNG/                                  اپ اندروید (فورک v2rayNG)
  app/src/main/java/com/v2ray/ang/simple/ صفحهٔ ساده: SimpleActivity, SimpleTestService, SimpleProbeEngine
AndroidLibXrayLite/                       هستهٔ Xray (ساب‌ماژول)
hev-socks5-tunnel/                        پل TUN (ساب‌ماژول)
docs/                                     یادداشت‌های فنی
```

## اعتبارها

- [v2rayNG](https://github.com/2dust/v2rayNG) — GPL-3.0 (README اصلی در `docs/README_upstream_v2rayNG.md`)
- [Xray-core](https://github.com/XTLS/Xray-core) — MPL-2.0
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — MIT
- فونت [وزیرمتن](https://github.com/rastikerdar/vazirmatn) — SIL OFL 1.1

## لایسنس

GPL-3.0 — فایل [LICENSE](LICENSE) را ببینید. اگر این برنامه یا نسخهٔ تغییریافتهٔ آن را توزیع می‌کنید، باید سورس را در دسترس بگذارید.
