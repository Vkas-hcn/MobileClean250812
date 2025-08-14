package com.neglected.tasks.undertaken

import android.app.Application
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig

class MvcPApp: Application() {
    companion object{
        lateinit var instance: MvcPApp
    }
    override fun onCreate() {
        super.onCreate()
        instance= this
        val config = ImagePipelineConfig.newBuilder(this)
            .setDownsampleEnabled(true)
            .build()
        Fresco.initialize(this, config)
    }
}