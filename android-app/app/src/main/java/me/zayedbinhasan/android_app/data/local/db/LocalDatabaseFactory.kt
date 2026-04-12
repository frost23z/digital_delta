package me.zayedbinhasan.android_app.data.local.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import me.zayedbinhasan.data.Database

object LocalDatabaseFactory {
    private const val DEFAULT_DATABASE_NAME = "digital_delta.db"

    fun create(context: Context): Database {
        val driver = AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = DEFAULT_DATABASE_NAME,
        )
        return Database(driver)
    }
}
