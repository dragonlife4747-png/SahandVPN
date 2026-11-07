# Sahand VPN — حالت ساده (دو دکمه)

این نسخه از Sahand VPN یک صفحهٔ واحد دارد و هیچ اسمی از کانفیگ‌ها نمایش نمی‌دهد.

## رفتار برنامه

1. **اولین اجرا:** لینک سابسکرابیشن پیش‌فرض
   `https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/refs/heads/main/all_configs.txt`
   دانلود و همهٔ کانفیگ‌ها (حدود ۱۱٬۰۰۰) داخل برنامه ذخیره می‌شود. هیچ‌کدام در UI دیده نمی‌شوند.
2. **دکمهٔ «تست کانفیگ‌ها»:**
   - مرحلهٔ ۱: سرورهای یکتا (EndPoint) با اتصال TCP سریع غربال می‌شوند.
   - مرحلهٔ ۲: روی سرورهای در دسترس، «تأخیر واقعی» با اجرای هستهٔ Xray و یک درخواست HTTP از داخل تونل اندازه‌گیری می‌شود.
   - به‌محض پیدا شدن **۵ کانفیگ سالم** تست متوقف می‌شود و نتیجه ذخیره می‌شود.
   - شمارنده‌ها: تعداد تست‌شده و تعداد سالم.
3. **دکمهٔ «اتصال»:** فقط بعد از تست فعال می‌شود و به کانفیگی با **کمترین تأخیر واقعی** وصل می‌شود.
   اگر اتصال با آن کانفیگ شکست بخورد، به‌صورت خودکار سراغ کانفیگ سالم بعدی می‌رود (حداکثر ۵ تلاش).
4. **قطع اتصال:** همان دکمه در حالت متصل.
5. **به‌روزرسانی:** دکمهٔ گوشهٔ بالا سابسکرابیشن را دوباره دانلود می‌کند.

## پارامترهای تست (قابل تغییر در `SimpleConfig.kt`)

| پارامتر | مقدار | توضیح |
|---|---|---|
| `TARGET_WORKING` | 5 | تعداد کانفیگ سالم لازم برای فعال شدن اتصال |
| `TCP_CONCURRENCY` | 8 | تعداد اتصال TCP همزمان در غربال اولیه |
| `CORE_CONCURRENCY` | 2 | تست واقعی همزمان (هر تست یک هستهٔ Xray جدا می‌سازد) |
| `PROBE_TIMEOUT_MS` | 10s | سقف زمانی هر تست واقعی |
| `SCAN_CHUNK` | 120 | تعداد EndPoint در هر دور غربال |
| `MAX_ENDPOINTS_PER_RUN` | 800 | سقف EndPoint هر اجرای تست |
| `PROBE_URL` | `http://www.gstatic.com/generate_204` | آدرس تست (HTTP بدون TLS برای سرعت) |

## نکات پیاده‌سازی

- صفحهٔ ساده `com.v2ray.ang.simple.SimpleActivity` است و در Manifest به‌عنوان LAUNCHER ثبت شده؛
  `MainActivity` اصلی هنوز وجود دارد (برای تنظیمات پیشرفته) ولی لانچر نیست.
- تست در سرویس `SimpleTestService` و در پروسهٔ جداگانهٔ `:tasks` اجرا می‌شود تا کتابخانهٔ native
  در پروسهٔ UI لود نشود.
- نتیجهٔ تست در `files/simple_state.json` ذخیره می‌شود (نه MMKV) چون باید بلافاصله بین پروسهٔ
  تست و پروسهٔ UI دیده شود.
- `SettingsManager.initAssets` هم در `SimpleActivity` و هم قبل از استارت هسته در
  `CoreServiceManager.launchCore` صدا زده می‌شود؛ بدون آن فایل‌های `geosite.dat/geoip.dat`
  کپی نمی‌شوند و Xray با خطای `failed to open geosite.dat` بالا نمی‌آید.

## ساخت

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd V2rayNG
./gradlew :app:assemblePlaystoreRelease
# خروجی: app/build/outputs/apk/playstore/release/SahandVPN_1.2_<abi>.apk
```

## برندینگ در نوتیفیکیشن

- عنوان نوتیفیکیشن سرویس، `R.string.app_name` («Sahand VPN») است، نه `ProfileItem.remarks`
  (نام کانفیگ نباید هیچ‌جا دیده شود).
- آیکون کوچک نوتیفیکیشن `drawable/ic_stat_sahand.xml` است (سپر خط‌دار با تیک، مونوکروم).
  مراجع قبلی `ic_stat_name` / `ic_stat_proxy` / `ic_stat_direct` در
  `NotificationManager`، `NotificationHelper`، `QSTileService` و Manifest جایگزین شدند.
- متن «v2rayNG» در همهٔ `res/values*/strings.xml` (۹ فایل) به «Sahand VPN» تغییر کرد.
  شناسهٔ فنی `v2rayng://` (برای ایمپورت لینک) و User-Agent شبکه دست‌نخورده ماند.

## رابط کاربری (نسخهٔ ۱.۳)

- فونت: `res/font/vazirmatn_regular|medium|bold.ttf` و `FontFamily` بالای `SimpleActivity`.
- رنگ‌ها به‌صورت توکن‌های فایل‌سطح (`BgTop`, `CardBg`, `TextSecondary`, `AccentGreen`, ...) تعریف شده‌اند؛
  متن ثانویه روی پس‌زمینهٔ `#070B14` کنتراست کافی دارد (`#B7C5DB` و `#93A3BC`).
- اندازه‌ها: عنوان حلقه ۲۵sp، توضیح وضعیت ۱۵.۵sp با `lineHeight` ۲۵sp، برچسب کارت ۱۲.۵sp — همه با فونت وزیرمتن.
- حلقه: `Canvas` با `drawArc` + `Brush.sweepGradient`، چرخش با `rememberInfiniteTransition` هنگام تست/اتصال،
  و `clickable` روی خود حلقه به‌عنوان میان‌بر اتصال.
- دکمه‌ها `Button` متریال نیستند؛ `Box` با گرادیان/حاشیه و `clickable` هستند تا گرادیان و سایهٔ رنگی داشته باشند.

## نسخهٔ ۱.۴ — تعامل و دوزبانگی

- متن‌ها از `res/values/strings_simple.xml` (انگلیسی) و `res/values-fa/strings_simple.xml` (فارسی) می‌آیند؛
  هیچ رشتهٔ فارسی هاردکد در `SimpleActivity` نمانده است. جهت چیدمان از `LocalLayoutDirection` سیستم می‌آید.
- انتخاب زبان با `AppLocaleManager.setApplicationLanguage("auto"|"fa"|"en")` انجام می‌شود
  (همان مکانیزم per-app locale خود برنامه؛ اکتیویتی خودکار recreate می‌شود).
- اعداد با `num()` بر اساس زبان فرمت می‌شوند: ارقام فارسی + جداکنندهٔ «٬» برای fa، `%,d` برای بقیه.
- دکمهٔ به‌روزرسانی حذف شده و به **نگه‌داشتن آرم برنامه** منتقل شده است (`combinedClickable(onLongClick)`).
- شماره‌های مرحله (`۱`/`۲`) از `simple_step_one`/`simple_step_two` می‌آیند تا در انگلیسی `1`/`2` شوند.
- جست‌وجو: `SimpleTestService` هر نقطهٔ سالم را بلافاصله در `simple_state.json` ذخیره می‌کند؛
  اکتیویتی هر ۱٫۲ ثانیه فایل را می‌خواند و اگر ۲۵ ثانیه هیچ پیشرفتی نرسد، جست‌وجو را پایان‌یافته تلقی می‌کند
  (سرویس مرده یا قفل‌شده دیگر باعث گیر کردن UI روی «در حال جست‌وجو» نمی‌شود).

## موتور تست (نسخهٔ ۱.۵)

`SimpleProbeEngine` یک نمونهٔ Xray می‌سازد که برای هر کانفیگ یک **اوت‌باند** و یک **اینباند SOCKS روی 127.0.0.1**
دارد؛ بعد درخواست‌ها از طریق همان پورت‌های محلی موازی ارسال می‌شوند (OkHttp با `Proxy.Type.SOCKS`).
این کار هم سریع‌تر است و هم از قفل‌شدن هستهٔ native که با چند نمونهٔ هم‌زمان رخ می‌داد جلوگیری می‌کند.

- فیلتر پیش از ساخت کانفیگ: `vless` بدون TLS/REALITY روی IP، `shadowsocks` بدون پسورد یا با رمز پشتیبانی‌نشده،
  و هر اوت‌باندی که `tlsSettings.allowInsecure` دارد (در Xray 26 حذف شده) کنار گذاشته می‌شود.
- اگر با این حال هسته کانفیگ را رد کند، دسته نصف می‌شود و هر نیمه جدا تست می‌شود (bisect).
- مرحلهٔ سریع: تأخیر HTTP برای دسته‌های ۱۲تایی. مرحلهٔ نهایی: HTTPS + دانلود ۱۹۲KB برای ۶ کاندید برتر.
- رتبه‌بندی: `httpsOk` ← سرعت دانلود ← تأخیر.

### پایش سلامت

`CoreServiceManager.startHealthMonitor` پس از بالا آمدن تونل هر ۳۵ ثانیه یک درخواست ۲۰۴ از طریق SOCKS محلی می‌زند.
اگر شبکهٔ بالادست اینترنت داشته باشد و دو بررسی پشت‌سرهم شکست بخورد، `switchToNextPoint` نقطهٔ سالم بعدی را
از `simple_state.json` انتخاب می‌کند و `reloadCore()` هسته را در همان رابط VPN ری‌لود می‌کند.

## انیمیشن‌ها و تنظیمات گوشی (نسخهٔ ۱.۶)

`rememberInfiniteTransition` و `Animatable` به `animator_duration_scale` سیستم احترام می‌گذارند؛ روی گوشی‌هایی که
این مقدار صفر است (Developer options، Battery saver یا Accessibility → Remove animations) همهٔ موشن‌ها یخ می‌زنند.
برای همین در `SimpleActivity`:

- `rememberFrameAngle(active, periodMs)` چرخش را با `withFrameNanos` می‌سازد (مستقل از تنظیمات انیمیشن).
- `rememberFrameRamp(trigger, durationMs)` موجِ اتصال را با فریم‌کلاک اجرا می‌کند.
- اسپینر دکمه یک `Canvas` + `drawArc` با همان زاویه است، نه `CircularProgressIndicator`.
- در `onCreate` مقدار `Settings.Global.ANIMATOR_DURATION_SCALE` در لاگ‌کت ثبت می‌شود.

نکتهٔ تست: `adb shell settings put global animator_duration_scale 0` و سپس مقایسهٔ دو فریم از ناحیهٔ حلقه
(کراپ ۳۰۰×۳۰۰) — باید متفاوت باشند.
