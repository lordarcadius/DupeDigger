package com.vipuljha.dupedigger

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.vipuljha.dupedigger.core.designsystem.AppMotion
import com.vipuljha.dupedigger.data.ai.LiteRtEmbedder
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class DupeDiggerApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var embedder: Lazy<LiteRtEmbedder>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /** The one image loader: local files only, video frames for thumbnails, and the house image fade. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(AppMotion.ImageFadeMillis)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        embedder.get().onTrimMemory(level)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
