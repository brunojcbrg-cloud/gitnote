package io.github.wiiznokes.gitnote.data.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "Aberturas", primaryKeys = ["relativePath"])
data class Abertura(
    val relativePath: String,
    val abertaEmMillis: Long,
)

@Dao
interface HistoricoDao {
    @Upsert
    suspend fun upsert(abertura: Abertura)

    @Query("SELECT * FROM Aberturas")
    suspend fun todas(): List<Abertura>
}

/** Banco independente da reindexacao e das migracoes destrutivas de RepoDatabase. */
@Database(entities = [Abertura::class], version = 1, exportSchema = false)
abstract class HistoricoDatabase : RoomDatabase() {
    abstract val dao: HistoricoDao

    companion object {
        fun buildDatabase(context: Context): HistoricoDatabase = Room.databaseBuilder(
            context,
            HistoricoDatabase::class.java,
            "HistoricoDatabase",
        ).build()
    }
}
