package com.walkory.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface WalkoryDao {
    @Transaction @Query("SELECT * FROM inspections ORDER BY modifiedSeconds DESC, modifiedNanos DESC, id")
    fun observeInspections(): Flow<List<InspectionGraph>>
    @Transaction @Query("SELECT * FROM inspections WHERE id = :id")
    fun observeInspection(id: String): Flow<InspectionGraph?>
    @Transaction @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun inspection(id: String): InspectionGraph?
    @Insert suspend fun insertInspection(value: InspectionEntity)
    @Update suspend fun updateInspection(value: InspectionEntity)
    @Query("DELETE FROM inspections WHERE id = :id") suspend fun deleteInspection(id: String): Int

    @Insert suspend fun insertRooms(values: List<RoomEntity>)
    @Update suspend fun updateRooms(values: List<RoomEntity>)
    @Query("DELETE FROM rooms WHERE id IN (:ids)") suspend fun deleteRooms(ids: List<String>)
    @Insert suspend fun insertChecks(values: List<CheckEntity>)
    @Update suspend fun updateChecks(values: List<CheckEntity>)
    @Query("DELETE FROM checks WHERE id IN (:ids)") suspend fun deleteChecks(ids: List<String>)
    @Insert suspend fun insertIssues(values: List<IssueEntity>)
    @Update suspend fun updateIssues(values: List<IssueEntity>)
    @Query("DELETE FROM issues WHERE id IN (:ids)") suspend fun deleteIssues(ids: List<String>)
    @Insert suspend fun insertPhotos(values: List<PhotographEntity>)
    @Update suspend fun updatePhotos(values: List<PhotographEntity>)
    @Query("DELETE FROM photographs WHERE id IN (:ids)") suspend fun deletePhotos(ids: List<String>)
    @Insert suspend fun insertFiles(values: List<AttachmentFileEntity>)
    @Query("SELECT id FROM attachment_files WHERE pendingDeletion = 1 ORDER BY id") suspend fun pendingFiles(): List<String>
    @Query("DELETE FROM attachment_files WHERE id = :id AND pendingDeletion = 1") suspend fun forgetDeletedFile(id: String)

    @Query("SELECT * FROM properties ORDER BY name COLLATE NOCASE, id") fun observeProperties(): Flow<List<PropertyEntity>>
    @Query("SELECT id FROM attachment_files WHERE id IN (:ids)") suspend fun existingFiles(ids: List<String>): List<String>
    @Query("SELECT EXISTS(SELECT 1 FROM properties WHERE id = :id)") suspend fun propertyExists(id: String): Boolean
    @Upsert suspend fun saveProperty(value: PropertyEntity)
    @Query("DELETE FROM properties WHERE id = :id") suspend fun deleteProperty(id: String): Int

    @Transaction @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE, id") fun observeTemplates(): Flow<List<TemplateGraph>>
    @Upsert suspend fun saveTemplate(value: TemplateEntity)
    @Query("DELETE FROM template_checks WHERE templateId = :id") suspend fun deleteTemplateChecks(id: String)
    @Insert suspend fun insertTemplateChecks(values: List<TemplateCheckEntity>)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String): Int
}
