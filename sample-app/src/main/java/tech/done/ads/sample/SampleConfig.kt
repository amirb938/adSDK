package tech.done.ads.sample

object SampleConfig {
    object Urls {
        const val CONTENT_VIDEO =
            "https://dls7.iran-gamecenter-host.com/DonyayeSerial/movies/2010/tt1375666/SoftSub/Inception.2010.720p.BluRay.SoftSub.MkvCage.DonyayeSerial.mkv"
//        const val ADS_TAG = "https://amirb938.s3.ir-thr-at1.arvanstorage.ir/ads%2Fads_skipable.xml"
//        const val ADS_TAG = "https://ads.kianoosh.dev/ads/ads"
        const val ADS_TAG = "https://play-dev.huma.ir/api/ads/vmap?type=1&tag=provider:lenz&tag=genre:Comedy&tag=BND_TVSeri&tag=BND_RAMCMD&movieId=155160&episodeId=293252&tag=type:1&userId=54283185-22d7-4136-8d8a-e50fa6802fa0&sdkType=dma&adProvider=internal&originatedService=play-movie&device-model=55DSL-SMART"
    }

    object Assets {
        const val VAST = "sample_vast.xml"
        const val VMAP = "sample_vmap.xml"
        const val VMAP_SIMID = "vmap_simid.xml"
        const val VMAP_SIMID_NO_SKIP = "vmap_simid_no_skip.xml"
    }
}

