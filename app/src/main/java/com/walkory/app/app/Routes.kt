package com.walkory.app.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
internal enum class MainArea { INSPECTIONS, OBJECTS, TEMPLATES, SETTINGS }

@Serializable
internal data class AreaRoute(val area: MainArea) : NavKey

// IDs only; live content remains in repository-backed screen state.
@Serializable
internal data class RecordRoute(val area: MainArea, val id: String) : NavKey

@Serializable
internal enum class Information { HELP, ABOUT, LOCAL_DATA }

@Serializable
internal data class InformationRoute(val page: Information) : NavKey
