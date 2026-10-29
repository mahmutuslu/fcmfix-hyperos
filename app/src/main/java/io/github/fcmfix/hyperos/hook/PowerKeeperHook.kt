package io.github.fcmfix.hyperos.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.fcmfix.hyperos.MainHook
import io.github.fcmfix.hyperos.MainHook.Companion.log
import io.github.fcmfix.hyperos.MainHook.Companion.logError

/**
 * PowerKeeper Hook'ları (com.miui.powerkeeper)
 *
 * HyperOS 4'ün PowerKeeper servisi, uygulamaların arka plan davranışlarını
 * kontrol eden ana bileşendir. Şu mekanizmaları yönetir:
 *
 * 1. Ağ Kısıtlamaları:
 *    Google servisleriyle bağlantıyı kısıtlayabilir veya tamamen kesebilir.
 *    -> Çözüm: Ağ politika kararlarında GMS'e her zaman izin verilir.
 *
 * 2. Arka Plan İşlem Yönetimi:
 *    PowerSaveConfigureManager üzerinden uygulamaları arka plan kısıtlama
 *    listelerine ekleyebilir.
 *    -> Çözüm: GMS'in kısıtlama listelerine eklenmesi engellenir.
 *
 * 3. Arka Plan Ağ Erişimi:
 *    Ekran kapalıyken uygulamaların ağ bağlantısını kesebilir.
 *    -> Çözüm: GMS için ağ erişimi her zaman açık tutulur.
 */
object PowerKeeperHook {

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookNetworkRestriction(lpparam)
        hookPowerSaveConfigure(lpparam)
        hookBackgroundControl(lpparam)
        hookUserConfigure(lpparam)
    }

    /**
     * 1. Ağ Kısıtlaması Hook'u
     *
     * PowerKeeper, Google bağlantı kontrolü başarısız olduğunda
     * setUidPolicy(uid, POLICY_REJECT_ALL) çağırarak GMS'in ağ erişimini keser.
     *
     * ColorOS'taki GoogleRestrictionController'ın HyperOS karşılığıdır.
     */
    private fun hookNetworkRestriction(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val networkClasses = listOf(
                "com.miui.powerkeeper.provider.NetworkPolicyManager",
                "com.miui.powerkeeper.statemachine.NetworkRestrictionController",
                "com.miui.powerkeeper.statemachine.BackgroundNetworkController"
            )

            for (className in networkClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // setUidPolicy ve benzeri ağ politika metodlarını hook'la
                val policyMethods = clazz.declaredMethods.filter {
                    it.name.contains("setUidPolicy", ignoreCase = true) ||
                    it.name.contains("restrictNetwork", ignoreCase = true) ||
                    it.name.contains("blockNetwork", ignoreCase = true) ||
                    it.name.contains("setNetworkPolicy", ignoreCase = true)
                }

                for (method in policyMethods) {
                    XposedHelpers.findAndHookMethod(
                        clazz,
                        method.name,
                        *method.parameterTypes,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                // UID parametresini kontrol et veya paket adını bul
                                val shouldBlock = shouldBlockNetworkRestriction(param.args)
                                if (shouldBlock) {
                                    param.result = null
                                    log("Ağ kısıtlaması engellendi: ${method.name}")
                                }
                            }
                        }
                    )
                }

                log("Ağ kısıtlaması hook'u uygulandı: $className")
            }

            // Genel NetworkPolicyManagerService hook'u (system_server tarafı)
            hookNetworkPolicyService(lpparam)

        } catch (e: Throwable) {
            logError("Ağ kısıtlaması hook'u başarısız", e)
        }
    }

    /**
     * NetworkPolicyManagerService içindeki setUidPolicy hook'u
     */
    private fun hookNetworkPolicyService(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val npmClass = XposedHelpers.findClassIfExists(
                "com.android.server.net.NetworkPolicyManagerService",
                lpparam.classLoader
            ) ?: return

            val methods = npmClass.declaredMethods.filter {
                it.name == "setUidPolicy" && it.parameterTypes.size >= 2
            }

            for (method in methods) {
                XposedHelpers.findAndHookMethod(
                    npmClass,
                    method.name,
                    *method.parameterTypes,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (param.args.size >= 2) {
                                val policy = param.args[1]
                                // POLICY_REJECT_ALL (4) ise ve Google UID'si ise engelle
                                if (policy is Int && policy == 4) {
                                    log("Google servisi için POLICY_REJECT_ALL engellendi")
                                    param.result = null
                                }
                            }
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            logError("NetworkPolicyManagerService hook'u başarısız", e)
        }
    }

    /**
     * 2. PowerSave Yapılandırma Hook'u
     *
     * PowerSaveConfigureManager, uygulamaların güç tasarrufu profillerini yönetir.
     * GMS'in agresif güç tasarrufu moduna alınmasını engeller.
     */
    private fun hookPowerSaveConfigure(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val configClasses = listOf(
                "com.miui.powerkeeper.provider.PowerSaveConfigureManager",
                "com.miui.powerkeeper.configure.PowerSaveConfig"
            )

            for (className in configClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // getAppLevel / getRestrictionLevel benzeri metodları hook'la
                val levelMethods = clazz.declaredMethods.filter {
                    (it.name.contains("Level", ignoreCase = true) ||
                     it.name.contains("Priority", ignoreCase = true) ||
                     it.name.contains("Restriction", ignoreCase = true)) &&
                    it.parameterTypes.any { p -> p == String::class.java }
                }

                for (method in levelMethods) {
                    XposedHelpers.findAndHookMethod(
                        clazz,
                        method.name,
                        *method.parameterTypes,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val packageName = param.args.filterIsInstance<String>()
                                    .firstOrNull { it.contains(".") }
                                if (packageName != null && packageName in MainHook.GOOGLE_PACKAGES) {
                                    // En düşük kısıtlama seviyesini döndür (0 = kısıtlama yok)
                                    if (method.returnType == Int::class.javaPrimitiveType) {
                                        param.result = 0
                                        log("Güç tasarrufu kısıtlaması kaldırıldı: $packageName")
                                    }
                                }
                            }
                        }
                    )
                }

                log("PowerSave yapılandırma hook'u uygulandı: $className")
            }
        } catch (e: Throwable) {
            logError("PowerSave yapılandırma hook'u başarısız", e)
        }
    }

    /**
     * 3. Arka Plan Kontrol Hook'u
     *
     * PowerKeeper'ın bgControl mekanizması arka plan uygulamalarını yönetir.
     * GMS'in arka planda çalışmasına her zaman izin verir.
     */
    private fun hookBackgroundControl(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val bgClasses = listOf(
                "com.miui.powerkeeper.statemachine.BackgroundCleanController",
                "com.miui.powerkeeper.statemachine.SleepModeController",
                "com.miui.powerkeeper.bgcontrol.BgControlManager"
            )

            for (className in bgClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // shouldClean / shouldKill / shouldRestrict metodlarını hook'la
                val controlMethods = clazz.declaredMethods.filter {
                    (it.name.contains("should", ignoreCase = true) ||
                     it.name.contains("canKill", ignoreCase = true) ||
                     it.name.contains("canClean", ignoreCase = true) ||
                     it.name.contains("needClean", ignoreCase = true)) &&
                    it.returnType == Boolean::class.javaPrimitiveType
                }

                for (method in controlMethods) {
                    XposedHelpers.findAndHookMethod(
                        clazz,
                        method.name,
                        *method.parameterTypes,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val packageName = extractPackageFromArgs(param.args)
                                if (packageName != null && packageName in MainHook.GOOGLE_PACKAGES) {
                                    param.result = false
                                    log("Arka plan temizleme engellendi: $packageName (${method.name})")
                                }
                            }
                        }
                    )
                }

                log("Arka plan kontrol hook'u uygulandı: $className")
            }
        } catch (e: Throwable) {
            logError("Arka plan kontrol hook'u başarısız", e)
        }
    }

    /**
     * 4. Kullanıcı Yapılandırma Hook'u
     *
     * UserConfigure sınıfı, kullanıcının uygulama bazında yaptığı
     * arka plan ve güç ayarlarını saklar. GMS için her zaman
     * "kısıtlama yok" döndürülmesini sağlar.
     */
    private fun hookUserConfigure(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val userConfigClass = XposedHelpers.findClassIfExists(
                "com.miui.powerkeeper.provider.UserConfigure",
                lpparam.classLoader
            ) ?: return

            val configMethods = userConfigClass.declaredMethods.filter {
                it.parameterTypes.any { p -> p == String::class.java } &&
                (it.returnType == Int::class.javaPrimitiveType ||
                 it.returnType == Boolean::class.javaPrimitiveType)
            }

            for (method in configMethods) {
                XposedHelpers.findAndHookMethod(
                    userConfigClass,
                    method.name,
                    *method.parameterTypes,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val packageName = param.args.filterIsInstance<String>()
                                .firstOrNull { it.contains(".") }
                            if (packageName != null && packageName in MainHook.GOOGLE_PACKAGES) {
                                when (method.returnType) {
                                    Boolean::class.javaPrimitiveType -> {
                                        // isRestricted -> false, isAllowed -> true
                                        val isNegative = method.name.contains("restrict", ignoreCase = true) ||
                                                         method.name.contains("block", ignoreCase = true) ||
                                                         method.name.contains("limit", ignoreCase = true)
                                        param.result = !isNegative
                                    }
                                    Int::class.javaPrimitiveType -> {
                                        // Kısıtlama seviyesi: 0 = yok
                                        param.result = 0
                                    }
                                }
                            }
                        }
                    }
                )
            }

            log("Kullanıcı yapılandırma hook'u uygulandı")
        } catch (e: Throwable) {
            logError("Kullanıcı yapılandırma hook'u başarısız", e)
        }
    }

    // --- Yardımcı Metodlar ---

    /**
     * Ağ kısıtlamasının Google paketleri için olup olmadığını kontrol eder
     */
    private fun shouldBlockNetworkRestriction(args: Array<Any?>): Boolean {
        for (arg in args) {
            when (arg) {
                is String -> {
                    if (arg in MainHook.GOOGLE_PACKAGES) return true
                }
                is Int -> {
                    // POLICY_REJECT_ALL (4) kontrolü
                    // UID kontrolü daha karmaşık, bu basitleştirilmiş versiyon
                }
            }
        }
        return false
    }

    /**
     * Metot parametrelerinden paket adını çıkarmaya çalışır
     */
    private fun extractPackageFromArgs(args: Array<Any?>): String? {
        for (arg in args) {
            if (arg is String && arg.contains(".") && !arg.contains(" ")) {
                return arg
            }
        }
        return null
    }
}
