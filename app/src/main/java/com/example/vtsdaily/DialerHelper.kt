package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

object DialerHelper {

    // Add/adjust for your device

    fun launchDialerChooser(context: Context, phone: String) {
        val clean = phone.trim()
        if (clean.isBlank()) return

        val uri = "tel:$clean".toUri()
        val base = Intent(Intent.ACTION_DIAL, uri)

        val chooser = Intent.createChooser(base, "Call with…")
        context.startActivity(chooser)
    }




    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
}
