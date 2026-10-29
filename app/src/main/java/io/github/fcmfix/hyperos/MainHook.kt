package io.github.fcmfix.hyperos

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.fcmfix.hyperos.hook.FrameworkHook
import io.github.fcmfix.hyperos.hook.PowerKeeperHook

/**
 * FCMFix HyperOS 4 - Ana Xposed Hook Giriş Noktası
 *
 * Bu modül, Xiaomi HyperOS 4 cihazlarda FCM (Firebase Cloud Messaging)
 * bildirimlerinin güvenilir şekilde alınmasını sağlar.
 *
 * İki kapsamda çalışır:
 * 1. android (system_server) - Yayın kısıtlamaları, otomatik başlatma ve
 *    cihaz bekleme beyaz listesi
 * 2. com.miui.powerkeeper - Ağ kısıtlamaları ve işlem dondurma
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "FCMFix-HyperOS4"
        const val PACKAGE_GMS = "com.google.android.gms"
        const val PACKAGE_GSF = "com.google.android.gsf"
        const val PACKAGE_PLAY_STORE = "com.android.vending"

        val GOOGLE_PACKAGES = setOf(
            PACKAGE_GMS,
            PACKAGE_GSF,
            PACKAGE_PLAY_STORE
        )

        fun log(message: String) {
            XposedBridge.log("[$TAG] $message")
        }

        fun logError(message: String, throwable: Throwable? = null) {
            XposedBridge.log("[$TAG] HATA: $message")
            throwable?.let { XposedBridge.log(it) }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            // Sistem çerçevesi hook'ları
            "android" -> {
                log("Sistem çerçevesi hook'ları başlatılıyor...")
                FrameworkHook.init(lpparam)
            }
            // PowerKeeper hook'ları
            "com.miui.powerkeeper" -> {
                log("PowerKeeper hook'ları başlatılıyor...")
                PowerKeeperHook.init(lpparam)
            }
        }
    }
}
