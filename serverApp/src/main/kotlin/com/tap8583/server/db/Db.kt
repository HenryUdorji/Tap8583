package com.tap8583.server.db

import com.tap8583.server.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Postgres access: a small connection pool, schema migrations on start, and
 * a few JDBC helpers. Queries run on the IO dispatcher.
 */
class Db(val dataSource: DataSource) {

    suspend fun <T> tx(block: Connection.() -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val result = conn.block()
                conn.commit()
                result
            } catch (t: Throwable) {
                conn.rollback()
                throw t
            }
        }
    }

    companion object {
        fun connect(config: DatabaseConfig, maxPoolSize: Int = 10): Db {
            val hikari = HikariConfig().apply {
                jdbcUrl = jdbcUrl(config.url)
                config.user?.let { username = it }
                config.password?.let { password = it }
                maximumPoolSize = maxPoolSize
                poolName = "tap8583"
            }
            val dataSource = HikariDataSource(hikari)
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            return Db(dataSource)
        }

        /** Accepts both JDBC URLs and the postgres://user:pass@host/db form many hosts hand out. */
        fun jdbcUrl(url: String): String {
            if (url.startsWith("jdbc:")) return url
            val uri = java.net.URI(url.replaceFirst(Regex("^postgres(ql)?://"), "http://"))
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val userInfo = uri.userInfo?.split(":", limit = 2)
            val params = buildList {
                userInfo?.getOrNull(0)?.let { add("user=$it") }
                userInfo?.getOrNull(1)?.let { add("password=$it") }
                uri.query?.let { add(it) }
            }
            val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
            return "jdbc:postgresql://${uri.host}$port${uri.path}$query"
        }
    }
}

fun Connection.update(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { it.bind(params); it.executeUpdate() }

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { stmt ->
        stmt.bind(params)
        stmt.executeQuery().use { rs ->
            val out = mutableListOf<T>()
            while (rs.next()) out += map(rs)
            out
        }
    }

fun <T> Connection.queryOne(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
    query(sql, *params, map = map).firstOrNull()

private fun PreparedStatement.bind(params: Array<out Any?>) {
    params.forEachIndexed { i, value ->
        val index = i + 1
        when (value) {
            null -> setObject(index, null)
            is Instant -> setTimestamp(index, Timestamp.from(value))
            is UUID -> setObject(index, value)
            is Json -> setObject(index, value.text, java.sql.Types.OTHER)
            else -> setObject(index, value)
        }
    }
}

/** Marks a string parameter as JSON so it binds to a jsonb column. */
@JvmInline
value class Json(val text: String)

fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()
fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)
