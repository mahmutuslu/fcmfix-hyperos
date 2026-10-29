package io.github.fcmfix.hyperos.hook

import android.content.Intent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.fcmfix.hyperos.MainHook
import io.github.fcmfix.hyperos.MainHook.Companion.log
import io.github.fcmfix.hyperos.MainHook.Companion.logError

/**
 * Sistem Çerçevesi (system_server) Hook'ları
 *
 * HyperOS 4'ün sistem çerçevesindeki aşağıdaki kısıtlamaları devre dışı bırakır:
 *
 * 1. FLAG_STOPPED Yayın Kısıtlaması:
 *    Durmuş uygulamalara FCM yayınlarının ulaşmasını engeller.
 *    -> Çözüm: GMS kaynaklı yayınlarda FLAG_INCLUDE_STOPPED_PACKAGES eklenir.
 *
 * 2. Otomatik Başlatma Kısıtlaması:
 *    MIUI/HyperOS otomatik başlatma izni olmayan uygulamaların arka planda
 *    başlatılmasını engeller.
 *    -> Çözüm: AutoStartManagerServiceStubImpl hook'lanarak FCM kaynaklı
 *    başlatmalar için izin verilir.
 *
 * 3. Cihaz Bekleme (Doze) Kısıtlaması:
 *    Derin uyku modunda GMS ağ erişimi kısıtlanır.
 *    -> Çözüm: DeviceIdleController'ın beyaz listesine GMS eklenir.
 *
 * 4. Greezer/Aurogon İşlem Dondurma:
 *    HyperOS arka plandaki GMS işlemlerini dondurup FCM bağlantısını koparır.
 *    -> Çözüm: GreezeManagerService hook'lanarak GMS dondurması engellenir.
 */
object FrameworkHook {

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookBroadcastRestriction(lpparam)
        hookAutoStartRestriction(lpparam)
        hookDeviceIdleWhitelist(lpparam)
        hookGreezerFreeze(lpparam)
        hookMilletRestriction(lpparam)
    }

    /**
     * 1. Yayın Kısıtlamasını Atla (FLAG_STOPPED)
     *
     * Android'de durmuş (stopped) bir uygulamaya yayın gönderilemez.
     * GMS, FCM mesajı aldığında hedef uygulamayı uyandırmak için yayın gönderir.
     * Ancak HyperOS agresif şekilde uygulamaları durdurduğundan, yayınlar ulaşamaz.
     *
     * Bu hook, GMS kaynaklı yayınlarda FLAG_INCLUDE_STOPPED_PACKAGES bayrağını
     * otomatik ekleyerek durmuş uygulamaların da yayın almasını sağlar.
     */
    private fun hookBroadcastRestriction(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // ActivityManagerService içindeki yayın gönderme metodunu hook'la
            val amsClass = XposedHelpers.findClassIfExists(
                "com.android.server.am.ActivityManagerService",
                lpparam.classLoader
            )

            if (amsClass == null) {
                logError("ActivityManagerService sınıfı bulunamadı")
                return
            }

            // broadcastIntentLocked veya broadcastIntent metodunu bul
            val broadcastMethods = amsClass.declaredMethods.filter {
                it.name.contains("broadcastIntent") && it.parameterTypes.any { p ->
                    p == Intent::class.java
                }
            }

            for (method in broadcastMethods) {
                XposedHelpers.findAndHookMethod(
                    amsClass,
                    method.name,
                    *method.parameterTypes,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args.firstOrNull { it is Intent } as? Intent
                                ?: return

                            val callerPackage = param.args.filterIsInstance<String>()
                                .firstOrNull { it.contains("google") }

                            // GMS kaynaklı FCM yayınlarını tespit et
                            if (callerPackage != null || isGmsIntent(intent)) {
                                // Durmuş uygulamalara da yayını ilet
                                val currentFlags = intent.flags
                                if (currentFlags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES == 0) {
                                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                    // FLAG_EXCLUDE_STOPPED_PACKAGES'ı kaldır
                                    if (currentFlags and Intent.FLAG_EXCLUDE_STOPPED_PACKAGES != 0) {
                                        intent.flags = intent.flags and Intent.FLAG_EXCLUDE_STOPPED_PACKAGES.inv()
                                    }
                                    log("Yayın kısıtlaması atlandı: ${intent.action} -> ${intent.`package` ?: "tüm"}")
                                }
                            }
                        }
                    }
                )
            }

            log("Yayın kısıtlaması hook'u başarıyla uygulandı")
        } catch (e: Throwable) {
            logError("Yayın kısıtlaması hook'u başarısız", e)
        }
    }

    /**
     * 2. Otomatik Başlatma Kısıtlamasını Atla
     *
     * HyperOS'ta uygulamaların arka planda başlatılması otomatik başlatma izni gerektirir.
     * AutoStartManagerServiceStubImpl sınıfı bu kontrolü yapar.
     * FCM kaynaklı başlatmalar için bu kontrolü devre dışı bırakıyoruz.
     */
    private fun hookAutoStartRestriction(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // HyperOS 4 otomatik başlatma yöneticisi
            val autoStartClasses = listOf(
                "com.android.server.am.AutoStartManagerServiceStubImpl",
                "com.android.server.am.MiuiAutoStartManagerService",
                "com.android.server.am.ExtraActivityManagerService"
            )

            var hooked = false
            for (className in autoStartClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // isAllowStartService / isAllowStartFromBindService hook'la
                val methodNames = listOf(
                    "isAllowStartService",
                    "isAllowStartFromBindService",
                    "isAllowStartFromBroadcast",
                    "isAllowAutoStart",
                    "shouldPreventStartService",
                    "shouldPreventStartActivity"
                )

                for (methodName in methodNames) {
                    try {
                        val methods = clazz.declaredMethods.filter { it.name == methodName }
                        for (method in methods) {
                            XposedHelpers.findAndHookMethod(
                                clazz,
                                methodName,
                                *method.parameterTypes,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam) {
                                        // Hedef paketin korumalı olup olmadığını kontrol et
                                        val packageName = extractPackageName(param.args)
                                        if (packageName != null && isProtectedPackage(packageName)) {
                                            // "should prevent" metodları için false,
                                            // "is allow" metodları için true döndür
                                            param.result = if (methodName.startsWith("should")) false else true
                                            log("Otomatik başlatma izni verildi: $packageName ($methodName)")
                                        }
                                    }
                                }
                            )
                            hooked = true
                        }
                    } catch (_: Throwable) {
                        // Bu metot bu sınıfta yok, devam et
                    }
                }

                if (hooked) break
            }

            if (hooked) {
                log("Otomatik başlatma kısıtlaması hook'u başarıyla uygulandı")
            } else {
                logError("Otomatik başlatma yöneticisi sınıfı bulunamadı")
            }
        } catch (e: Throwable) {
            logError("Otomatik başlatma kısıtlaması hook'u başarısız", e)
        }
    }

    /**
     * 3. Cihaz Bekleme (Doze) Beyaz Listesi
     *
     * Android'in Doze modu ve HyperOS'un MiuiDeviceIdleHelper'ı, cihaz boştayken
     * uygulamaların ağ erişimini kısıtlar. Bu, FCM bağlantısının kopmasına yol açar.
     *
     * Bu hook, GMS paketlerini otomatik olarak Doze beyaz listesine ekler.
     */
    private fun hookDeviceIdleWhitelist(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val idleClasses = listOf(
                "com.android.server.DeviceIdleController",
                "com.android.server.MiuiDeviceIdleHelper",
                "com.android.server.DeviceIdleControllerStubImpl"
            )

            for (className in idleClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // getNewWhiteList veya benzeri beyaz liste metodlarını hook'la
                val whitelistMethods = clazz.declaredMethods.filter {
                    it.name.contains("WhiteList", ignoreCase = true) ||
                    it.name.contains("Whitelist", ignoreCase = true) ||
                    it.name.contains("whitelist", ignoreCase = true) ||
                    it.name.contains("AllowList", ignoreCase = true)
                }

                for (method in whitelistMethods) {
                    if (method.returnType == Array<String>::class.java ||
                        method.returnType == List::class.java ||
                        method.returnType == Set::class.java ||
                        method.returnType == MutableList::class.java
                    ) {
                        XposedHelpers.findAndHookMethod(
                            clazz,
                            method.name,
                            *method.parameterTypes,
                            object : XC_MethodHook() {
                                @Suppress("UNCHECKED_CAST")
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    val result = param.result ?: return
                                    when (result) {
                                        is Array<*> -> {
                                            val list = result.toMutableList()
                                            var modified = false
                                            for (pkg in MainHook.GOOGLE_PACKAGES) {
                                                if (!list.contains(pkg)) {
                                                    list.add(pkg)
                                                    modified = true
                                                }
                                            }
                                            if (modified) {
                                                param.result = list.toTypedArray()
                                                log("Bekleme beyaz listesine Google paketleri eklendi")
                                            }
                                        }
                                        is MutableList<*> -> {
                                            val list = result as MutableList<String>
                                            for (pkg in MainHook.GOOGLE_PACKAGES) {
                                                if (!list.contains(pkg)) {
                                                    list.add(pkg)
                                                    log("Bekleme beyaz listesine eklendi: $pkg")
                                                }
                                            }
                                        }
                                        is MutableSet<*> -> {
                                            val set = result as MutableSet<String>
                                            for (pkg in MainHook.GOOGLE_PACKAGES) {
                                                if (set.add(pkg)) {
                                                    log("Bekleme beyaz listesine eklendi: $pkg")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                log("Cihaz bekleme hook'u uygulandı: $className")
            }
        } catch (e: Throwable) {
            logError("Cihaz bekleme beyaz listesi hook'u başarısız", e)
        }
    }

    /**
     * 4. Greezer/Aurogon İşlem Dondurma Hook'u
     *
     * HyperOS'un Greezer/Aurogon altyapısı arka plan işlemlerini agresif şekilde dondurur.
     * GMS dondurulduğunda FCM bağlantısı kopar ve bildirimler gecikir.
     *
     * Bu hook, GMS paketlerinin dondurulmasını engeller.
     */
    private fun hookGreezerFreeze(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val greezerClasses = listOf(
                "com.android.server.am.GreezeManagerService",
                "com.android.server.am.AurogonImmobulusMode",
                "com.android.server.am.MiuiFreezeManager"
            )

            for (className in greezerClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // triggerQuickFreeze ve benzeri dondurma metodlarını hook'la
                val freezeMethods = clazz.declaredMethods.filter {
                    it.name.contains("freeze", ignoreCase = true) ||
                    it.name.contains("Freeze", ignoreCase = true) ||
                    it.name.contains("suspend", ignoreCase = true)
                }

                for (method in freezeMethods) {
                    XposedHelpers.findAndHookMethod(
                        clazz,
                        method.name,
                        *method.parameterTypes,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                // Parametre olarak paket adı veya UID içerip içermediğini kontrol et
                                val packageName = extractPackageName(param.args)
                                if (packageName != null && packageName in MainHook.GOOGLE_PACKAGES) {
                                    param.result = null // Dondurma işlemini iptal et
                                    log("Dondurma engellendi: $packageName (${method.name})")
                                }
                            }
                        }
                    )
                }

                // mNoRestrictAppSet'e Google paketlerini enjekte et
                try {
                    val noRestrictField = clazz.declaredFields.find {
                        it.name.contains("NoRestrict", ignoreCase = true) ||
                        it.name.contains("noRestrict", ignoreCase = true) ||
                        it.name.contains("whitelist", ignoreCase = true) ||
                        it.name.contains("allowList", ignoreCase = true)
                    }
                    if (noRestrictField != null) {
                        noRestrictField.isAccessible = true
                        // Constructor veya init sonrası set'e ekleme yap
                        val initMethods = clazz.declaredMethods.filter {
                            it.name == "onStart" || it.name == "systemReady" ||
                            it.name.contains("init", ignoreCase = true)
                        }
                        for (initMethod in initMethods) {
                            XposedHelpers.findAndHookMethod(
                                clazz,
                                initMethod.name,
                                *initMethod.parameterTypes,
                                object : XC_MethodHook() {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun afterHookedMethod(param: MethodHookParam) {
                                        try {
                                            val field = noRestrictField.get(param.thisObject)
                                            if (field is MutableSet<*>) {
                                                val set = field as MutableSet<String>
                                                for (pkg in MainHook.GOOGLE_PACKAGES) {
                                                    if (set.add(pkg)) {
                                                        log("Kısıtlamasız listeye eklendi: $pkg")
                                                    }
                                                }
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                }
                            )
                        }
                    }
                } catch (_: Throwable) {}

                log("Greezer/Aurogon hook'u uygulandı: $className")
            }
        } catch (e: Throwable) {
            logError("Greezer dondurma hook'u başarısız", e)
        }
    }

    /**
     * 5. MILLET Kısıtlama Sistemi Hook'u
     *
     * HyperOS'un MILLET sistemi, uygulamaları çeşitli kısıtlama kategorilerine sokar.
     * MILLET_NO_RESTRICT_APP ayarı üzerinden kısıtlamasız uygulamalar belirlenir.
     *
     * Bu hook, Google paketlerinin her zaman kısıtlamasız kabul edilmesini sağlar.
     */
    private fun hookMilletRestriction(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val milletClasses = listOf(
                "com.android.server.am.ProcessPolicy",
                "com.android.server.am.ProcessPolicyManager",
                "com.android.server.am.MilletConfig"
            )

            for (className in milletClasses) {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                    ?: continue

                // isRestricted / shouldRestrict benzeri metodları hook'la
                val restrictMethods = clazz.declaredMethods.filter {
                    it.name.contains("restrict", ignoreCase = true) ||
                    it.name.contains("Restrict", ignoreCase = true)
                }

                for (method in restrictMethods) {
                    if (method.returnType == Boolean::class.javaPrimitiveType) {
                        XposedHelpers.findAndHookMethod(
                            clazz,
                            method.name,
                            *method.parameterTypes,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val packageName = extractPackageName(param.args)
                                    if (packageName != null && packageName in MainHook.GOOGLE_PACKAGES) {
                                        // isRestricted -> false, shouldRestrict -> false
                                        param.result = false
                                        log("MILLET kısıtlaması devre dışı: $packageName")
                                    }
                                }
                            }
                        )
                    }
                }

                log("MILLET hook'u uygulandı: $className")
            }
        } catch (e: Throwable) {
            logError("MILLET kısıtlama hook'u başarısız", e)
        }
    }

    // --- Yardımcı Metodlar ---

    /**
     * Intent'in GMS/FCM kaynaklı olup olmadığını kontrol eder
     */
    private fun isGmsIntent(intent: Intent): Boolean {
        val action = intent.action ?: return false
        return action.contains("google") ||
               action.contains("gcm") ||
               action.contains("fcm") ||
               action.contains("c2dm") ||
               action == "com.google.android.c2dm.intent.RECEIVE" ||
               action == "com.google.firebase.MESSAGING_EVENT" ||
               action == "com.google.firebase.INSTANCE_ID_EVENT" ||
               intent.`package` in MainHook.GOOGLE_PACKAGES
    }

    /**
     * Metot parametrelerinden paket adını çıkarmaya çalışır
     */
    private fun extractPackageName(args: Array<Any?>): String? {
        for (arg in args) {
            when (arg) {
                is String -> {
                    if (arg.contains(".") && !arg.contains(" ") && !arg.contains("/")) {
                        return arg
                    }
                }
                is Intent -> {
                    arg.component?.packageName?.let { return it }
                    arg.`package`?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * Paketin korumalı olup olmadığını kontrol eder
     * (Google paketleri + kullanıcı tarafından seçilen uygulamalar)
     */
    private fun isProtectedPackage(packageName: String): Boolean {
        return packageName in MainHook.GOOGLE_PACKAGES
        // TODO: SharedPreferences'tan kullanıcı seçimlerini de kontrol et
    }
}
