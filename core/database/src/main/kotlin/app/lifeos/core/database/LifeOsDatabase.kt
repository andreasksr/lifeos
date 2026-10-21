package app.lifeos.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lifeos.core.database.dao.IntegrityDao
import app.lifeos.core.database.dao.LedgerDao
import app.lifeos.core.database.dao.LifeDao
import app.lifeos.core.database.dao.LinkDao
import app.lifeos.core.database.dao.NodeDao
import app.lifeos.core.database.dao.PlanningDao
import app.lifeos.core.database.dao.ProjectDao
import app.lifeos.core.database.entity.AssetEntity
import app.lifeos.core.database.entity.CalendarEventEntity
import app.lifeos.core.database.entity.DocumentEntity
import app.lifeos.core.database.entity.ExamEntity
import app.lifeos.core.database.entity.StudySessionEntity
import app.lifeos.core.database.entity.SubjectEntity
import app.lifeos.core.database.entity.TopicEntity
import app.lifeos.core.database.entity.VehicleEntity
import app.lifeos.core.database.entity.VehicleEventEntity
import app.lifeos.core.database.entity.MilestoneEntity
import app.lifeos.core.database.entity.ProjectEntity
import app.lifeos.core.database.entity.ReminderEntity
import app.lifeos.core.database.entity.TaskCompletionEntity
import app.lifeos.core.database.entity.TaskEntity
import app.lifeos.core.database.dao.SettingsDao
import app.lifeos.core.database.entity.AccountEntity
import app.lifeos.core.database.entity.BudgetEntity
import app.lifeos.core.database.entity.CategoryEntity
import app.lifeos.core.database.entity.LinkEntity
import app.lifeos.core.database.entity.NodeEntity
import app.lifeos.core.database.entity.RecurringBookingEntity
import app.lifeos.core.database.entity.RecurringPaymentEntity
import app.lifeos.core.database.entity.TotalBudgetEntity
import app.lifeos.core.database.entity.SettingEntity
import app.lifeos.core.database.entity.SplitEntity
import app.lifeos.core.database.entity.TransactionEntity
import app.lifeos.core.database.schema.Migrations
import app.lifeos.core.database.schema.TriggerInstaller
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        NodeEntity::class,
        LinkEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SplitEntity::class,
        BudgetEntity::class,
        SettingEntity::class,
        RecurringPaymentEntity::class,
        RecurringBookingEntity::class,
        TotalBudgetEntity::class,
        ProjectEntity::class,
        MilestoneEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        CalendarEventEntity::class,
        ReminderEntity::class,
        VehicleEntity::class,
        VehicleEventEntity::class,
        AssetEntity::class,
        DocumentEntity::class,
        SubjectEntity::class,
        TopicEntity::class,
        StudySessionEntity::class,
        ExamEntity::class,
    ],
    version = LifeOsDatabase.VERSION,
    exportSchema = true,
)
abstract class LifeOsDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun linkDao(): LinkDao
    abstract fun settingsDao(): SettingsDao
    abstract fun integrityDao(): IntegrityDao
    abstract fun planningDao(): PlanningDao
    abstract fun projectDao(): ProjectDao
    abstract fun lifeDao(): LifeDao

    companion object {
        const val VERSION = 4
        const val FILE_NAME = "lifeos.db"

        /**
         * Öffnet die verschlüsselte Datenbank (SQLCipher, ADR-004).
         * Es gibt bewusst KEIN `fallbackToDestructiveMigration` – fehlt eine Migration, stürzt die
         * App lieber kontrolliert ab, als Nutzerdaten zu löschen.
         */
        fun open(context: Context, passphrase: ByteArray, name: String = FILE_NAME): LifeOsDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context.applicationContext, LifeOsDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
                .addCallback(SchemaCallback)
                .addMigrations(*Migrations.ALL)
                .build()
        }

        /** Unverschlüsselte In-Memory-DB für instrumentierte Tests. */
        fun inMemory(context: Context): LifeOsDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, LifeOsDatabase::class.java)
                .addCallback(SchemaCallback)
                .build()
    }
}

/**
 * Installiert die Trigger bei Neuanlage und bei jedem Öffnen neu (löschen + anlegen). So ist
 * garantiert, dass immer exakt die Definitionen aus `SchemaSql` aktiv sind – auch nach Updates.
 */
internal object SchemaCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        TriggerInstaller.reinstall(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            TriggerInstaller.reinstall(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
