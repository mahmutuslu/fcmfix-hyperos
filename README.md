# FCMFix - HyperOS 4 (Türkçe)

Bu modül, [zopulus/fcmfix-coloros17](https://github.com/zopulus/fcmfix-coloros17) ve [kooritea/fcmfix](https://github.com/kooritea/fcmfix) projeleri temel alınarak **Xiaomi HyperOS 4 (Android 17 / HyperOS 2.0-4.0 mimarisi)** için uyarlanmış ve **tamamen Türkçe** hale getirilmiş bir LSPosed/Xposed modülüdür.

---

## 🎯 Ne İşe Yarar?

Xiaomi HyperOS (özellikle Çin ROM'larında ve agresif pil kısıtlaması uygulanan sürümlerde):
1. Arka planda Google Play Hizmetleri'nin (`com.google.android.gms`) ağ bağlantısını keser veya dondurur (**Greezer / Aurogon**).
2. Uygulama kapalıyken (`FLAG_STOPPED`) gelen FCM bildirim yayınlarını bloke eder (`Failed to broadcast to stopped app`).
3. Otomatik başlatma izni verilmemiş uygulamaların FCM aracılığıyla uyandırılmasını engeller (`AutoStartManagerService`).
4. **PowerKeeper** servisi ekran kapandığında Google servislerini derin uykuya alarak `mtalk.google.com` soketini koparır.

Bu modül, ColorOS'taki kısıtlama atlatma mantığını HyperOS 4'ün sistem servislerine uyarlayarak FCM bildirimlerinin anında ve eksiksiz ulaşmasını sağlar.

---

## 🔄 ColorOS 17'den HyperOS 4'e Yapılan Değişiklikler

| Özellik / Mekanizma | Orijinal ColorOS 17 Modülü | HyperOS 4 Uyarlaması |
|---|---|---|
| **Kapsam (Scope)** | `android`, `com.oplus.battery` | `android`, `com.miui.powerkeeper` |
| **Başlatma Yönetimi** | `OplusAppStartupManager`, `isAppClassifyRestricted` | `AutoStartManagerServiceStubImpl`, `ExtraActivityManagerService` |
| **Ağ Kısıtlaması** | `GoogleRestrictionController` (`POLICY_REJECT_ALL`) | `NetworkPolicyManagerService`, `PowerKeeper NetworkRestrictionController` |
| **Dondurma (Freeze)** | `OplusBgSceneManager`, `OplusHansDBConfig` | `GreezeManagerService`, `AurogonImmobulusMode`, `mNoRestrictAppSet` |
| **Bekleme (Doze)** | `OplusDeviceIdleHelper` | `DeviceIdleController`, `MiuiDeviceIdleHelper` |
| **Arayüz Dili** | Çince / İngilizce | **Tamamen Türkçe (tr-TR)** |

---

## 📦 Proje Mimarisi

```
fcmfix-hyperos4/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── xposed_init                   # Xposed giriş noktası kaydı
│       ├── java/io/github/fcmfix/hyperos/
│       │   ├── MainHook.kt                   # Ana hook dağıtıcı
│       │   ├── hook/
│       │   │   ├── FrameworkHook.kt          # system_server (Broadcast, AutoStart, Doze, Greezer)
│       │   │   └── PowerKeeperHook.kt        # com.miui.powerkeeper (Ağ, Güç tasarrufu)
│       │   └── ui/
│       │       └── MainActivity.kt           # Türkçe Material 3 durum paneli
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── item_hook_card.xml
│           └── values/
│               ├── strings.xml               # Türkçe metin dizgileri
│               ├── arrays.xml                # LSPosed otomatik kapsamları
│               ├── colors.xml
│               └── themes.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 Kurulum ve Kullanım

1. **Gereksinimler:**
   - Root yetkisi (**KernelSU**, **APatch** veya **Magisk**)
   - Çalışan bir Zygisk ve **LSPosed** ortamı (veya LSPosed Modern / Zygisk-Next)

2. **APK Kurulumu:**
   - Projeyi derleyip APK'yı cihaza yükleyin (veya Release APK'sını kurun).

3. **LSPosed Üzerinden Etkinleştirme:**
   - LSPosed uygulamasını açın.
   - **FCMFix - HyperOS 4** modülünü bulun ve etkinleştirin.
   - Kapsam (Scope) listesinde şu iki hedefin işaretli olduğundan emin olun:
     - `Sistem Çerçevesi (android)`
     - `PowerKeeper (com.miui.powerkeeper)`
   - Cihazınızı yeniden başlatın.

4. **Kontrol (FCM Tanılama):**
   - Telefon arama ekranına `*#*#426#*#*` yazarak **Google Play Hizmetleri FCM Tanılama** ekranını açın.
   - Ekranı kilitleyip birkaç dakika bekledikten sonra açtığınızda durumun `CONNECTED` olarak kaldığını görebilirsiniz.
