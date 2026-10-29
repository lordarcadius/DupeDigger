package com.vipuljha.dupedigger.data.ai

import android.content.Context
import com.vipuljha.dupedigger.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Models live in internal storage: LiteRT-LM can't open a file the app keeps in its external files folder (D-006). */
@Singleton
class ModelStorage @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    val fetcher = ModelFetcher(File(context.filesDir, "models"), ioDispatcher)
}
