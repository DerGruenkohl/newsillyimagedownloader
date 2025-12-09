package com.dergruenkohl.newsillyimagedownloader.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.time.measureTime

class DatabaseService {
    private val logger = KotlinLogging.logger {  }
    init {
        // Create data directory if it doesn't exist
        File("data").mkdirs()
        val datasource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:./data/femboys"
            driverClassName = "org.h2.Driver"
            username = "meow"
            password = "mrrp"
            maximumPoolSize = 10
        })
        Database.connect(datasource)

        transaction {
            SchemaUtils.create(FemboyTable)
        }
    }

    fun insertFemboy(femboy: Femboy) {
        transaction {
            FemboyTable.insert {
                it[originalId] = femboy.id
                it[tags] = femboy.tags.joinToString(",")
                it[rating] = femboy.rating
                it[fileUrl] = femboy.fileUrl
            }
        }
    }
    fun insertFemboys(femboys: List<Femboy>) {
        transaction {
            FemboyTable.batchInsert(femboys) { femboy ->
                this[FemboyTable.originalId] = femboy.id
                this[FemboyTable.tags] = femboy.tags.joinToString(",")
                this[FemboyTable.rating] = femboy.rating
                this[FemboyTable.fileUrl] = femboy.fileUrl
                this[FemboyTable.names] = femboy.names.joinToString(";")
            }
        }
    }
    fun getFemboysWithTag(tags: List<String>): List<Femboy> {
        logger.info { "Loading images for tags: ${tags.joinToString(",")}" }
        var femboys = emptyList<Femboy>()
        val time = measureTime {
            femboys = tags.flatMap { tag ->
                getFemboysWithTag(tag)
            }.distinctBy { it.id }.filter { it.tags.containsAll(tags) }
        }
        logger.info { "Loaded ${femboys.size} images in $time" }
        return femboys
    }
    fun getFemboysWithTag(tag: String): List<Femboy> {
        return transaction {
            FemboyDao.find {
                FemboyTable.tags like "%$tag%"
            }.map { it.toFemboy() }
        }
    }

    fun getAstolfos(): List<Femboy> {
        return transaction {
            FemboyDao.find {
                FemboyTable.tags like "%astolfo%" or(FemboyTable.names like "%astolfo%") and (FemboyTable.tags notLike "%ai_generated%")
            }.map { it.toFemboy() }
        }
    }

    fun getAllFemboys(): List<Femboy> {
        return transaction {
            FemboyTable.selectAll().map { row ->
                Femboy(
                    id = row[FemboyTable.originalId],
                    tags = row[FemboyTable.tags].split(","),
                    rating = row[FemboyTable.rating],
                    fileUrl = row[FemboyTable.fileUrl],
                    names = row[FemboyTable.names].split(";")
                )
            }
        }
    }
    fun findAI(): List<Femboy> {
        return transaction {
            FemboyDao.find {
                FemboyTable.tags like "%ai_generated%"
            }.map { it.toFemboy() }
        }
    }
}