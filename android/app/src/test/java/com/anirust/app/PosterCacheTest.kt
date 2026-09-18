package com.anirust.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anirust.app.data.local.createPosterImageLoader
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PosterCacheTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    @OptIn(ExperimentalCoilApi::class)
    fun coversUseMemoryAndPersistentDiskAcrossImageLoadersWithoutDownloadingAgain() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.cacheDir.resolve("posters").deleteRecursively()
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        val bytes =
            ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                .toByteArray()
        bitmap.recycle()
        var requests = 0
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requests++
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "image/png")
                        .header("Cache-Control", "no-store")
                        .body(bytes.toResponseBody())
                        .build()
                }
                .build()
        fun loader() = createPosterImageLoader(context).newBuilder().okHttpClient(client).build()
        val request =
            ImageRequest.Builder(context)
                .data("https://images.test/poster.png")
                .size(40, 60)
                .allowHardware(false)
                .build()
        var images = loader()
        try {
            assertEquals(DataSource.NETWORK, (images.execute(request) as SuccessResult).dataSource)
            assertEquals(
                DataSource.MEMORY_CACHE,
                (images.execute(request) as SuccessResult).dataSource,
            )
            images.memoryCache!!.clear()
            assertEquals(DataSource.DISK, (images.execute(request) as SuccessResult).dataSource)
            images.shutdown()
            images =
                ImageLoader.Builder(context)
                    .diskCache(images.diskCache)
                    .okHttpClient(client)
                    .respectCacheHeaders(false)
                    .build()
            assertEquals(DataSource.DISK, (images.execute(request) as SuccessResult).dataSource)
            assertEquals(1, requests)
            assertEquals(150L * 1024 * 1024, images.diskCache!!.maxSize)
        } finally {
            images.shutdown()
            images.diskCache!!.clear()
            context.cacheDir.resolve("posters").deleteRecursively()
        }
    }
}
