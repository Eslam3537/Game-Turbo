package com.example.data

import kotlinx.coroutines.flow.Flow

class BoosterRepository(private val boosterDao: BoosterDao) {
    val allLogs: Flow<List<OptimizationLog>> = boosterDao.getAllLogs()
    val allGames: Flow<List<AddedGame>> = boosterDao.getAllGames()
    val allSessions: Flow<List<GameSessionRecord>> = boosterDao.getAllSessions()

    suspend fun insertLog(log: OptimizationLog) {
        boosterDao.insertLog(log)
    }

    suspend fun clearLogs() {
        boosterDao.clearAllLogs()
    }

    suspend fun insertGame(game: AddedGame) {
        boosterDao.insertGame(game)
    }

    suspend fun deleteGame(gameId: Int) {
        boosterDao.deleteGame(gameId)
    }

    suspend fun clearGames() {
        boosterDao.clearAllGames()
    }
}
