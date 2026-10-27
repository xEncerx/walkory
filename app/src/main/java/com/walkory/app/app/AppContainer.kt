package com.walkory.app.app

import android.content.Context
import com.walkory.app.data.preferences.createPreferencesStore
import com.walkory.app.platform.images.createLocalImageLoader

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val preferences = createPreferencesStore(applicationContext)
    val imageLoader = createLocalImageLoader(applicationContext)
}
