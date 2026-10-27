package com.walkory.app.platform.images

import android.content.Context
import coil3.ImageLoader

internal fun createLocalImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context).build()
