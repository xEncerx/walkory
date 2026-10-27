package com.walkory.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

internal fun createPreferencesStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("settings")
    }
