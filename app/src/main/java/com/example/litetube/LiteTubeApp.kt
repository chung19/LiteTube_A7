package com.example.litetube

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import com.example.litetube.net.OkHttpNewPipeDownloader

class LiteTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpNewPipeDownloader())
    }
}
