package dev.foco.bloqueio

import android.app.Application
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.QuotaTracker
import dev.foco.bloqueio.notify.NowBarNotifier

class BloqueioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RulesRepository.init(this)
        QuotaTracker.init(this)
        NowBarNotifier.createChannel(this)
    }
}
