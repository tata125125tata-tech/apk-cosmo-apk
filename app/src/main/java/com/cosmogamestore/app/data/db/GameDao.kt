package com.cosmogamestore.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM downloaded_games ORDER BY downloadDate DESC")
    fun getAllGamesFlow(): Flow<List<DownloadedGameEntity>>

    @Query("SELECT * FROM downloaded_games ORDER BY downloadDate DESC")
    suspend fun getAllGames(): List<DownloadedGameEntity>

    @Query("SELECT * FROM downloaded_games WHERE packageName = :packageName LIMIT 1")
    suspend fun getGameByPackage(packageName: String): DownloadedGameEntity?

    @Query("SELECT * FROM downloaded_games WHERE downloadId = :downloadId LIMIT 1")
    fun getGameByDownloadId(downloadId: Long): DownloadedGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGame(game: DownloadedGameEntity)

    @Query("UPDATE downloaded_games SET progress = :progress, fileSize = :fileSize WHERE downloadId = :downloadId")
    fun updateDownloadProgress(downloadId: Long, progress: Int, fileSize: Long)

    @Query("UPDATE downloaded_games SET status = :status WHERE downloadId = :downloadId")
    fun updateDownloadStatus(downloadId: Long, status: String)

    @Query("DELETE FROM downloaded_games WHERE packageName = :packageName")
    fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM downloaded_games WHERE filePath = :filePath")
    fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM downloaded_games WHERE downloadId = :downloadId")
    fun deleteByDownloadId(downloadId: Long)

    @Query("DELETE FROM downloaded_games")
    fun deleteAll()
}
