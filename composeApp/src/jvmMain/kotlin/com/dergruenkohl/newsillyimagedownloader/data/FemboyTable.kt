package com.dergruenkohl.newsillyimagedownloader.data

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable


object FemboyTable : IntIdTable("femboys") {
    val originalId = long("original_id")
    val tags = text("tags")
    val rating = enumeration("rating", Rating::class)
    val fileUrl = text("file_url")
    val names = text("name")
}