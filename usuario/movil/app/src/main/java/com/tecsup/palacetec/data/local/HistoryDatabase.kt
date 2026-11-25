package com.tecsup.palacetec.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.tecsup.palacetec.data.remote.HistoryItem

/** Última página conocida del historial. owner separa a los jugadores que usan el mismo celular. */
@Entity(tableName = "history", primaryKeys = ["owner", "source", "id"])
data class HistoryEntity(
    val owner: String,
    val source: String,
    val id: Long,
    val position: Int,
    val roundId: Long?,
    val kind: String,
    val selection: String,
    val chips: String,
    val amount: String,
    val payout: String?,
    val reference: String?,
    val createdAt: String,
    val roomId: Long?,
    val game: String,
    val status: String,
    val net: String?,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE owner = :owner ORDER BY position")
    suspend fun find(owner: String): List<HistoryEntity>

    @Insert
    suspend fun insert(items: List<HistoryEntity>)

    @Query("DELETE FROM history WHERE owner = :owner")
    suspend fun delete(owner: String)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Transaction
    suspend fun replace(owner: String, items: List<HistoryEntity>) {
        delete(owner)
        insert(items)
    }
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        /** Es solo una caché: ante un cambio de versión se vacía y se vuelve a pedir. */
        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

fun HistoryItem.toEntity(owner: String, position: Int) = HistoryEntity(
    owner = owner, source = source, id = id, position = position, roundId = roundId, kind = kind,
    selection = selection, chips = chips.joinToString(","), amount = amount, payout = payout,
    reference = reference, createdAt = createdAt, roomId = roomId, game = game, status = status, net = net,
)

fun HistoryEntity.toItem() = HistoryItem(
    id = id, roundId = roundId, kind = kind, selection = selection,
    chips = chips.split(",").mapNotNull { it.toIntOrNull() }, amount = amount, payout = payout,
    reference = reference, createdAt = createdAt, roomId = roomId, game = game, status = status,
    net = net, source = source,
)
