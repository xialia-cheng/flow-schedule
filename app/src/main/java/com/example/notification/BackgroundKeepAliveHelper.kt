package com.example.notification

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class OemBrand(val displayName: String) {
    COLOR_OS("ColorOS · OPPO / 一加 / 真我"),
    HYPER_OS("HyperOS / MIUI · 小米"),
    ORIGIN_OS("OriginOS / Funtouch · vivo / iQOO"),
    HARMONY_OS("HarmonyOS / MagicOS · 华为 / 荣耀"),
    GENERIC("通用安卓系统"),
}

object BackgroundKeepAliveHelper {

    fun detectOemBrand(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
    ): OemBrand {
        val m = manufacturer.trim().lowercase()
        val b = brand.trim().lowercase()
        val combined = "$m $b"
        return when {
            listOf("oppo", "oneplus", "realme").any { it in combined } -> OemBrand.COLOR_OS
            listOf("xiaomi", "redmi", "poco", "blackshark").any { it in combined } -> OemBrand.HYPER_OS
            listOf("vivo", "iqoo").any { it in combined } -> OemBrand.ORIGIN_OS
            listOf("huawei", "honor").any { it in combined } -> OemBrand.HARMONY_OS
            else -> OemBrand.GENERIC
        }
    }

    fun candidateAutoStartComponents(brand: OemBrand): List<Pair<String, String>> {
        return when (brand) {
            OemBrand.COLOR_OS -> listOf(
                "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
                "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                "com.oplus.battery" to "com.oplus.powermanager.fuelgaue.PowerControlActivity",
                "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            )
            OemBrand.HYPER_OS -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings",
            )
            OemBrand.ORIGIN_OS -> listOf(
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.PurviewTabActivity",
            )
            OemBrand.HARMONY_OS -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
                "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.hihonor.systemmanager" to "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity",
            )
            OemBrand.GENERIC -> emptyList()
        }
    }

    fun buildAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openAutoStartOrBackgroundSettings(context: Context): Boolean {
        val brand = detectOemBrand()
        for ((pkg, cls) in candidateAutoStartComponents(brand)) {
            val intent = Intent().apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return true
        }
        return runCatching {
            context.startActivity(buildAppDetailsIntent(context))
            true
        }.getOrDefault(false)
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val target = if (batteryIntent.resolveActivity(context.packageManager) != null) {
            batteryIntent
        } else {
            buildAppDetailsIntent(context)
        }
        return runCatching {
            context.startActivity(target)
            true
        }.getOrDefault(false)
    }

    fun applyExcludeFromRecents(context: Context, exclude: Boolean) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return
        runCatching {
            activityManager.appTasks.forEach { task ->
                runCatching { task.setExcludeFromRecents(exclude) }
            }
        }
    }

    fun oemKeepAliveGuideText(brand: OemBrand = detectOemBrand()): String {
        return when (brand) {
            OemBrand.COLOR_OS ->
                "ColorOS / 一加 / 真我建议：进入「自启动 / 耗电管理」开启「允许自启动」与「完全允许后台行为」，并在多任务卡片右上角点「··· → 锁定」防止划卡被杀。"
            OemBrand.HYPER_OS ->
                "小米 HyperOS / MIUI 建议：在「自启动管理」允许微澜日程自启动，将省电策略设为「无限制」，并在最近任务界面长按卡片点击「锁定」。"
            OemBrand.ORIGIN_OS ->
                "vivo / iQOO 建议：在 i 管家「权限管理 / 耗电管理」开启「允许自启动」与「允许后台高耗电」，并在多任务界面下拉应用卡片锁定。"
            OemBrand.HARMONY_OS ->
                "华为 / 荣耀建议：在系统管家「应用启动管理」关闭「自动管理」，手动开启「允许自启动、关联启动、后台活动」，并在多任务界面下拉锁定。"
            OemBrand.GENERIC ->
                "系统保活建议：请在系统「应用详情 / 电池管理」开启「允许自启动」与「后台无限制」，并在多任务界面锁定微澜日程。"
        }
    }
}
