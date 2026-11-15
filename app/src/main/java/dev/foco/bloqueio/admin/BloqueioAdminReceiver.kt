package dev.foco.bloqueio.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.foco.bloqueio.service.GuardService

class BloqueioAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        GuardService.start(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "A proteção só pode ser removida pelo Admin dentro do app Bloqueio."

    companion object {
        fun component(context: Context) = ComponentName(context, BloqueioAdminReceiver::class.java)
    }
}
