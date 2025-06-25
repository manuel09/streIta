package it.dogior.guardoserie

import com.lagradost.cloudstream3.Plugin
import com.lagradost.cloudstream3.mainPage
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.loadExtractor

@Plugin
class GuardoSeriePlugin : Plugin() {
    override fun load() {
        registerMainAPI(GuardoSerie())
    }
}
