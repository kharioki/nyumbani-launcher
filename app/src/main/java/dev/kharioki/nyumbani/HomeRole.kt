package dev.kharioki.nyumbani

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Asking Android to make Nyumbani the Home app, where the TV allows it. */
object HomeRole {
    /** True when the Home button already opens Nyumbani. */
    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    /**
     * The system's "Set as Home app?" prompt (Android 10+), or null when this TV doesn't offer it.
     * Many TVs leave it out; settings then shows the ADB commands instead.
     */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        val intent = roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
