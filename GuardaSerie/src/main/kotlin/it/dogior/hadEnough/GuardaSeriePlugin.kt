package it.dogior.guardaserie

import com.lagradost.cloudstream3.Plugin
import com.lagradost.cloudstream3.mainPage
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.loadExtractor

@Plugin
class GuardaSeriePlugin : Plugin() {
    override fun load() {
        registerMainAPI(GuardaSerie())
    }
}
