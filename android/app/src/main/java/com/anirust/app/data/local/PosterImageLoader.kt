package com.anirust.app.data.local

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

fun createPosterImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("posters"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        // Catalog posters use versioned URLs. Keep downloaded covers available offline.
        .respectCacheHeaders(false)
        .crossfade(240)
        .build()
