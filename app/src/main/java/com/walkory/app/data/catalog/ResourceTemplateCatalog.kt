package com.walkory.app.data.catalog

import android.content.Context
import android.content.res.Configuration
import com.walkory.app.R
import com.walkory.app.domain.*
import java.util.Locale
import kotlinx.coroutines.flow.map

/** Versioned app resources, never database seeds or live dependencies of copied content. */
internal class ResourceTemplateCatalog(private val context: Context) {
    fun templates(language: AppLanguage): List<ChecklistTemplate> {
        val configuration = Configuration(context.resources.configuration)
        when (language) {
            AppLanguage.ENGLISH -> configuration.setLocale(Locale.ENGLISH)
            AppLanguage.RUSSIAN -> configuration.setLocale(Locale.forLanguageTag("ru"))
            AppLanguage.SYSTEM -> Unit
        }
        val resources = context.createConfigurationContext(configuration).resources
        return rooms.flatMap { room -> InspectionRole.entries.map { role ->
            val key = room.key + "." + role.name.lowercase(Locale.ROOT)
            val roleTitle = if (role == InspectionRole.TENANT) R.string.catalog_tenant else R.string.catalog_landlord
            val guidance = if (role == InspectionRole.TENANT) R.string.catalog_tenant_guidance else R.string.catalog_landlord_guidance
            ChecklistTemplate(
                TemplateId("builtin:" + key),
                resources.getString(R.string.catalog_name, resources.getString(room.title), resources.getString(roleTitle)),
                TemplateOrigin.BuiltIn(key, role, room.type),
                room.checks.map { (title, detail) -> TemplateCheck(resources.getString(title), resources.getString(guidance, resources.getString(detail))) },
            )
        } }
    }

    private data class Room(val key: String, val type: RoomType, val title: Int, val checks: List<Pair<Int, Int>>)

    private companion object {
        val rooms = listOf(
            Room("kitchen_standard", RoomType.KITCHEN, R.string.catalog_room_kitchen_standard, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_window to R.string.catalog_guidance_window,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_worktop to R.string.catalog_guidance_worktop,
                R.string.catalog_check_sink to R.string.catalog_guidance_sink,
                R.string.catalog_check_cabinets to R.string.catalog_guidance_cabinets,
                R.string.catalog_check_cooking to R.string.catalog_guidance_cooking,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting,
                R.string.catalog_check_furniture to R.string.catalog_guidance_furniture,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent
            )),
            Room("kitchen_gas", RoomType.KITCHEN, R.string.catalog_room_kitchen_gas, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_window to R.string.catalog_guidance_window,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_worktop to R.string.catalog_guidance_worktop,
                R.string.catalog_check_sink to R.string.catalog_guidance_sink,
                R.string.catalog_check_cabinets to R.string.catalog_guidance_cabinets,
                R.string.catalog_check_cooking to R.string.catalog_guidance_cooking,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting,
                R.string.catalog_check_furniture to R.string.catalog_guidance_furniture,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent,
                R.string.catalog_check_gas to R.string.catalog_guidance_gas
            )),
            Room("bedroom", RoomType.BEDROOM, R.string.catalog_room_bedroom, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_window to R.string.catalog_guidance_window,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_bed to R.string.catalog_guidance_bed,
                R.string.catalog_check_storage to R.string.catalog_guidance_storage,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting,
                R.string.catalog_check_heating to R.string.catalog_guidance_heating
            )),
            Room("living_room", RoomType.LIVING_ROOM, R.string.catalog_room_living_room, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_window to R.string.catalog_guidance_window,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_upholstery to R.string.catalog_guidance_upholstery,
                R.string.catalog_check_storage to R.string.catalog_guidance_storage,
                R.string.catalog_check_electronics to R.string.catalog_guidance_electronics,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting,
                R.string.catalog_check_heating to R.string.catalog_guidance_heating
            )),
            Room("combined_bathroom", RoomType.COMBINED_BATHROOM, R.string.catalog_room_combined_bathroom, listOf(
                R.string.catalog_check_wet_surfaces to R.string.catalog_guidance_wet_surfaces,
                R.string.catalog_check_ceiling to R.string.catalog_guidance_ceiling,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_bath to R.string.catalog_guidance_bath,
                R.string.catalog_check_basin to R.string.catalog_guidance_basin,
                R.string.catalog_check_toilet to R.string.catalog_guidance_toilet,
                R.string.catalog_check_drainage to R.string.catalog_guidance_drainage,
                R.string.catalog_check_mirror to R.string.catalog_guidance_mirror,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent,
                R.string.catalog_check_appliances to R.string.catalog_guidance_appliances,
                R.string.catalog_check_lights_switches to R.string.catalog_guidance_lights_switches
            )),
            Room("bathroom", RoomType.BATHROOM, R.string.catalog_room_bathroom, listOf(
                R.string.catalog_check_wet_surfaces to R.string.catalog_guidance_wet_surfaces,
                R.string.catalog_check_ceiling to R.string.catalog_guidance_ceiling,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_bath to R.string.catalog_guidance_bath,
                R.string.catalog_check_basin to R.string.catalog_guidance_basin,
                R.string.catalog_check_drainage to R.string.catalog_guidance_drainage,
                R.string.catalog_check_mirror to R.string.catalog_guidance_mirror,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent,
                R.string.catalog_check_appliances to R.string.catalog_guidance_appliances,
                R.string.catalog_check_lights_switches to R.string.catalog_guidance_lights_switches
            )),
            Room("toilet", RoomType.TOILET, R.string.catalog_room_toilet, listOf(
                R.string.catalog_check_wet_surfaces to R.string.catalog_guidance_wet_surfaces,
                R.string.catalog_check_ceiling to R.string.catalog_guidance_ceiling,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_cistern to R.string.catalog_guidance_cistern,
                R.string.catalog_check_optional_basin to R.string.catalog_guidance_optional_basin,
                R.string.catalog_check_connections to R.string.catalog_guidance_connections,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent,
                R.string.catalog_check_lights_switches to R.string.catalog_guidance_lights_switches
            )),
            Room("entrance_hall", RoomType.ENTRANCE_HALL, R.string.catalog_room_entrance_hall, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_entrance to R.string.catalog_guidance_entrance,
                R.string.catalog_check_interior to R.string.catalog_guidance_interior,
                R.string.catalog_check_keys to R.string.catalog_guidance_keys,
                R.string.catalog_check_intercom to R.string.catalog_guidance_intercom,
                R.string.catalog_check_mirror to R.string.catalog_guidance_mirror,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting
            )),
            Room("balcony", RoomType.BALCONY, R.string.catalog_room_balcony, listOf(
                R.string.catalog_check_balcony_floor to R.string.catalog_guidance_balcony_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_glazing to R.string.catalog_guidance_glazing,
                R.string.catalog_check_access_door to R.string.catalog_guidance_access_door,
                R.string.catalog_check_railing to R.string.catalog_guidance_railing,
                R.string.catalog_check_balcony_storage to R.string.catalog_guidance_balcony_storage,
                R.string.catalog_check_optional_lighting to R.string.catalog_guidance_optional_lighting
            )),
            Room("walk_in_wardrobe", RoomType.WALK_IN_WARDROBE, R.string.catalog_room_walk_in_wardrobe, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_rails to R.string.catalog_guidance_rails,
                R.string.catalog_check_fittings to R.string.catalog_guidance_fittings,
                R.string.catalog_check_lights_switches to R.string.catalog_guidance_lights_switches,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent
            )),
            Room("storage_room", RoomType.STORAGE_ROOM, R.string.catalog_room_storage_room, listOf(
                R.string.catalog_check_floor_walls to R.string.catalog_guidance_floor_walls,
                R.string.catalog_check_ceiling to R.string.catalog_guidance_ceiling,
                R.string.catalog_check_lock to R.string.catalog_guidance_lock,
                R.string.catalog_check_shelves to R.string.catalog_guidance_shelves,
                R.string.catalog_check_lights_switches to R.string.catalog_guidance_lights_switches,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent
            )),
            Room("study", RoomType.STUDY, R.string.catalog_room_study, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_window to R.string.catalog_guidance_window,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_desk to R.string.catalog_guidance_desk,
                R.string.catalog_check_study_storage to R.string.catalog_guidance_study_storage,
                R.string.catalog_check_equipment to R.string.catalog_guidance_equipment,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting
            )),
            Room("laundry_utility_room", RoomType.LAUNDRY_UTILITY_ROOM, R.string.catalog_room_laundry_utility_room, listOf(
                R.string.catalog_check_wet_surfaces to R.string.catalog_guidance_wet_surfaces,
                R.string.catalog_check_ceiling to R.string.catalog_guidance_ceiling,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_utility_appliances to R.string.catalog_guidance_utility_appliances,
                R.string.catalog_check_drainage to R.string.catalog_guidance_drainage,
                R.string.catalog_check_study_storage to R.string.catalog_guidance_study_storage,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting,
                R.string.catalog_check_vent to R.string.catalog_guidance_vent
            )),
            Room("dining_room", RoomType.DINING_ROOM, R.string.catalog_room_dining_room, listOf(
                R.string.catalog_check_floor to R.string.catalog_guidance_floor,
                R.string.catalog_check_walls to R.string.catalog_guidance_walls,
                R.string.catalog_check_window to R.string.catalog_guidance_window,
                R.string.catalog_check_door to R.string.catalog_guidance_door,
                R.string.catalog_check_table to R.string.catalog_guidance_table,
                R.string.catalog_check_study_storage to R.string.catalog_guidance_study_storage,
                R.string.catalog_check_sockets to R.string.catalog_guidance_sockets,
                R.string.catalog_check_lighting to R.string.catalog_guidance_lighting
            ))
        )
    }
}

/** Combines durable authored content with read-only localized app content. */
internal class CatalogTemplateRepository(
    private val custom: TemplateRepository,
    private val catalog: ResourceTemplateCatalog,
) : TemplateRepository {
    override fun observeAll(language: AppLanguage): kotlinx.coroutines.flow.Flow<DomainResult<List<ChecklistTemplate>>> {
        val builtIns = catalog.templates(language)
        return custom.observeAll(language).map { result -> result.map { it + builtIns } }
    }

    override suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate> =
        if (template.origin != TemplateOrigin.Custom || template.id.value.startsWith("builtin:"))
            DomainResult.Failure(DomainError.ReadOnlyTemplate)
        else custom.saveCustom(template)

    override suspend fun deleteCustom(id: TemplateId): DomainResult<Unit> =
        if (id.value.startsWith("builtin:")) DomainResult.Failure(DomainError.ReadOnlyTemplate)
        else custom.deleteCustom(id)
}
