package com.walkory.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PropertyEntity::class, InspectionEntity::class, RoomEntity::class, CheckEntity::class,
        IssueEntity::class, AttachmentFileEntity::class, PhotographEntity::class, TemplateEntity::class, TemplateCheckEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class WalkoryDatabase : RoomDatabase() {
    abstract fun records(): WalkoryDao

    companion object {
        fun open(context: Context, name: String = "walkory.db"): WalkoryDatabase =
            Room.databaseBuilder(context.applicationContext, WalkoryDatabase::class.java, name)
                .addCallback(Constraints)
                .build()
    }
}

/** Cross-table invariants that Room annotations cannot express. Installed for every opened schema. */
private object Constraints : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS photograph_owner_insert BEFORE INSERT ON photographs
            WHEN (NEW.checkId IS NULL) = (NEW.issueId IS NULL)
            BEGIN SELECT RAISE(ABORT, 'A photograph must have exactly one owner'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS photograph_owner_update BEFORE UPDATE ON photographs
            WHEN (NEW.checkId IS NULL) = (NEW.issueId IS NULL)
            BEGIN SELECT RAISE(ABORT, 'A photograph must have exactly one owner'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS photograph_cleanup AFTER DELETE ON photographs
            BEGIN UPDATE attachment_files SET pendingDeletion = 1 WHERE id = OLD.fileId; END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS issue_state_insert BEFORE INSERT ON issues
            WHEN (SELECT state FROM checks WHERE id = NEW.checkId) != 'HAS_ISSUE'
            BEGIN SELECT RAISE(ABORT, 'Issues require HAS_ISSUE'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS issue_state_update BEFORE UPDATE ON issues
            WHEN (SELECT state FROM checks WHERE id = NEW.checkId) != 'HAS_ISSUE'
            BEGIN SELECT RAISE(ABORT, 'Issues require HAS_ISSUE'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS check_state_update BEFORE UPDATE OF state ON checks
            WHEN NEW.state != 'HAS_ISSUE' AND EXISTS(SELECT 1 FROM issues WHERE checkId = NEW.id)
            BEGIN SELECT RAISE(ABORT, 'Existing issues prevent this state'); END
        """.trimIndent())
    }
}
