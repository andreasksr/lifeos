package app.lifeos.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lifeos.core.database.dao.IntegrityDao
import app.lifeos.core.database.dao.LedgerDao
import app.lifeos.core.database.dao.LinkDao
import app.lifeos.core.database.dao.NodeDao
import app.lifeos.core.database.dao.SettingsDao
import app.lifeos.core.database.entity.AccountEntity
import app.lifeos.core.database.entity.BudgetEntity
import app.lifeos.core.database.entity.CategoryEntity
import app.lifeos.core.database.entity.LinkEntity
import app.lifeos.core.database.entity.NodeEntity
import app.lifeos.core.database.entity.SettingEntity
import app.lifeos.core.database.entity.SplitEntity
import app.lifeos.core.database.entity.TransactionEntity
import app.lifeos.core.database.schema.Migrations
import app.lifeos.core.database.schema.SchemaSql
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

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "lifeos.db"

        /**
         * Öffnet die verschlüsselte Datenbank (SQLCipher, ADR-004).
         * Es gibt bewusst KEIN `fallbackToDestructiveMigration` – fehlt eine Migration, stürzt die
         * App lieber kontrolliert ab, als Nutzerdaten zu löschen.
         */
        fun open(context: Context, passphrase: ByteArray): LifeOsDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context.applicationContext, LifeOsDatabase::class.java, FILE_NAME)
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

/** Legt Trigger an. `onOpen` repariert idempotent, falls ein Trigger fehlen sollte. */
internal object SchemaCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SchemaSql.TRIGGERS.forEach(db::execSQL)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        SchemaSql.TRIGGERS.forEach(db::execSQL)
    }
}
