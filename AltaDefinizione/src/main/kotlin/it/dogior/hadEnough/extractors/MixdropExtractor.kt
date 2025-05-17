package it.dogior.hadEnough.extractors

import com.lagradost.cloudstream3.extractors.MixDrop

class MyMixdropExtractor: MixDrop(){
    override var mainUrl = "https://mixdrop.my"
}