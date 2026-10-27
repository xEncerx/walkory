package com.walkory.app.data.local

import androidx.room.withTransaction
import com.walkory.app.domain.*

internal class RoomPropertyRepository(private val database: WalkoryDatabase) : PropertyRepository {
    private val dao = database.records()
    override fun observeAll() = dao.observeProperties().durableSnapshots { rows -> rows.map { it.toDomain() } }

    override suspend fun save(property: PropertyCard): DomainResult<PropertyCard> = storageResult {
        dao.saveProperty(PropertyEntity(property.id.value, property.name, property.address))
        DomainResult.Success(property)
    }

    override suspend fun deleteAndDetach(id: PropertyId): DomainResult<Unit> = storageResult {
        database.withTransaction {
            if (dao.deleteProperty(id.value) == 0) DomainResult.Failure(DomainError.NotFound(RecordKind.PROPERTY))
            else DomainResult.Success(Unit)
        }
    }
}

/** Owns only stored custom content. Catalog content is neither seeded nor stored in these tables. */
internal class RoomTemplateRepository(private val database: WalkoryDatabase) : TemplateRepository {
    private val dao = database.records()
    override fun observeAll(language: AppLanguage) = dao.observeTemplates().durableSnapshots { rows -> rows.map { it.toDomain() } }

    override suspend fun saveCustom(template: ChecklistTemplate): DomainResult<ChecklistTemplate> = storageResult {
        if (template.origin != TemplateOrigin.Custom) return@storageResult DomainResult.Failure(DomainError.ReadOnlyTemplate)
        if (template.checks.isEmpty()) return@storageResult DomainResult.Failure(DomainError.EmptyTemplate)
        database.withTransaction {
            dao.saveTemplate(TemplateEntity(template.id.value, template.name))
            dao.deleteTemplateChecks(template.id.value)
            dao.insertTemplateChecks(template.checks.mapIndexed { position, check -> TemplateCheckEntity(template.id.value, position, check.title, check.guidance) })
            DomainResult.Success(template)
        }
    }

    override suspend fun deleteCustom(id: TemplateId): DomainResult<Unit> = storageResult {
        database.withTransaction {
            if (dao.deleteTemplate(id.value) == 0) DomainResult.Failure(DomainError.NotFound(RecordKind.TEMPLATE))
            else DomainResult.Success(Unit)
        }
    }
}
