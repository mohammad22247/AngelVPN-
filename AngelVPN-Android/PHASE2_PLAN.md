# فاز ۲ — اتصال موتور DNSTT

## هدف
اولین موتور واقعی: **DNSTT** (KCP + Noise) تا ترافیک واقعاً از تونل رد شود.

## مراحل فنی

### 1. آماده‌سازی Go client
```bash
# فورک/کلون dnstt (مثلاً anonvector/dnstt یا upstream)
git clone https://github.com/anonvector/dnstt.git
cd dnstt

# ساخت AAR با gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
gomobile bind -target=android -androidapi=26 -o dnstt.aar ./dnstt-client
```

خروجی `dnstt.aar` را در `app/libs/` کپی کنید و در `app/build.gradle.kts`:

```kotlin
implementation(files("libs/dnstt.aar"))
```

### 2. Bridge Kotlin
فایل جدید: `vpn/engine/DnsttBridge.kt`

```kotlin
object DnsttBridge {
    fun start(profile: Profile, tunFd: Int, localSocksPort: Int = 1080) {
        // فراخوانی متدهای gomobile:
        // - set domain, pubkey, resolver list
        // - listen SOCKS5 on 127.0.0.1:1080
    }
    fun stop() { ... }
}
```

### 3. tun2socks / hev-socks5-tunnel
برای تبدیل TUN → SOCKS5 یکی از این‌ها:
- **hev-socks5-tunnel** (C، سبک)
- **badvpn tun2socks**
- **gVisor netstack** (مثل SlipNet)

پیشنهاد: hev-socks5-tunnel (لایسنس MIT، باینری‌های آماده arm64/armeabi).

جریان:
```
App traffic → TUN (VpnService)
           → hev-socks5-tunnel → 127.0.0.1:1080 (SOCKS5)
           → dnstt-client (Go) → DNS queries → Server
```

### 4. تغییرات AngelVpnService
در `startTunnel`:
```kotlin
when (profile.engine) {
    Engine.DNSTT, Engine.SLIPNET -> {
        // اگر subtype slipstream بود بعداً جدا می‌شود
        DnsttBridge.start(profile, tun!!.fd)
        startTun2Socks(tun!!, socksPort = 1080)
    }
    else -> { /* هنوز */ }
}
```

### 5. تست
- یک سرور dnstt شخصی بالا بیاورید
- لینک `dnst://domain?key=PUBKEY` ایمپورت
- اتصال → چک کنید IP عمومی عوض شده

## فاز ۳ (بعدی)
- SoarBridge (از Lantern soar یا SlipNet SoarBridge)
- CottenBridge / MasterDnsBridge (از WhiteDNS / CottenDNS engine AAR)
- Slipstream (Rust via cargo-ndk)

## منابع کد باز مفید
- https://github.com/anonvector/SlipNet (مرجع معماری VpnService)
- https://github.com/anonvector/dnstt
- https://github.com/WhiteDNS/CottenDNS
- https://github.com/dnstt-xyz/dnstt_xyz_app
