package com.zhousl.aether.data.chatdb

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createIosChatHistoryDatabase(path: String): ChatHistoryDatabase =
    Room.databaseBuilder<ChatHistoryDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
