package com.dergruenkohl.newsillyimagedownloader.data

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Serializable
data class Femboy(
    val id: Long,
    val tags: List<String>,
    val rating: Rating,
    val fileUrl: String,
    val names: List<String>

)
@Serializable
enum class Rating {
    SFW, NSFW, QUESTIONABLE;
    companion object {
        fun fromString(input: String): Rating{
            return when (input.lowercase()) {
                "g" -> SFW
                "e" -> NSFW
                "q", "s" -> QUESTIONABLE
                else -> throw IllegalArgumentException("Unknown rating: $input")
            }
        }
    }
}
class FemboyDao(id: EntityID<Int>): IntEntity(id){
    companion object: IntEntityClass<FemboyDao>(FemboyTable)
    var originalId by FemboyTable.originalId
    var tags by FemboyTable.tags
    var rating by FemboyTable.rating
    var fileUrl by FemboyTable.fileUrl
    var names by FemboyTable.names
    fun toFemboy(): Femboy {
        return Femboy(
            id = originalId,
            tags = tags.split(",").map { it.trim() },
            rating = rating,
            fileUrl = fileUrl,
            names = names.split(";")
        )
    }
}
