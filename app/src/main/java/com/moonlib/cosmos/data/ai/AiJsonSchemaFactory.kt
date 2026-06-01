package com.moonlib.cosmos.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一 JSON Schema 工厂。
 */
object AiJsonSchemaFactory {

    fun chatRepliesSchema(schemaName: String = "cosmos_replies"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("sender", JSONObject().apply { put("type", "string") })
                put("replies", JSONArrayItemsSchema(JSONObject().apply {
                    put("type", "object")
                    put("additionalProperties", true)
                    put("properties", JSONObject().apply {
                        put("type", JSONObject().apply { put("type", "string") })
                        put("time", JSONObject().apply { put("type", "string") })
                        put("content", JSONObject().apply { put("type", "string") })
                        put("extra", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().put("type").put("time").put("content"))
                }))
                put("status", JSONObject().apply {
                    put("type", "object")
                    put("additionalProperties", true)
                })
            })
            put("required", JSONArray().put("replies"))
            put("_schema_name", schemaName)
        }
    }

    fun socialRepliesSchema(schemaName: String = "cosmos_social_replies"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("replies", JSONArrayItemsSchema(JSONObject().apply {
                    put("type", "object")
                    put("additionalProperties", true)
                    put("properties", JSONObject().apply {
                        put("character_id", JSONObject().apply { put("type", "string") })
                        put("reply_to_username", JSONObject().apply { put("type", "string") })
                        put("content", JSONObject().apply { put("type", "string") })
                        put("parent_id", JSONObject().apply { put("type", "string") })
                        put("time_offset_seconds", JSONObject().apply { put("type", "integer") })
                    })
                    put("required", JSONArray().put("character_id").put("content").put("parent_id").put("time_offset_seconds"))
                }))
            })
            put("required", JSONArray().put("replies"))
            put("_schema_name", schemaName)
        }
    }

    fun diarySchema(schemaName: String = "cosmos_diary"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("content", JSONObject().apply { put("type", "string") })
                put("summary", JSONObject().apply { put("type", "string") })
                put("nextTime", JSONObject().apply { put("type", "string") })
                put("status", JSONObject().apply {
                    put("type", "object")
                    put("additionalProperties", true)
                })
            })
            put("required", JSONArray().put("content").put("summary").put("nextTime"))
            put("_schema_name", schemaName)
        }
    }

    fun timeSkipOnlineSchema(schemaName: String = "cosmos_time_skip_online"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("simulated_messages", JSONArrayItemsSchema(JSONObject().apply {
                    put("type", "object")
                    put("additionalProperties", true)
                    put("properties", JSONObject().apply {
                        put("character_id", JSONObject().apply { put("type", "string") })
                        put("type", JSONObject().apply { put("type", "string") })
                        put("time", JSONObject().apply { put("type", "string") })
                        put("content", JSONObject().apply { put("type", "string") })
                        put("extra", JSONObject().apply { put("type", "string") })
                    })
                }))
                put("simulated_moments", JSONArrayItemsSchema(socialPostSchema()))
                put("simulated_tweets", JSONArrayItemsSchema(socialPostSchema()))
            })
            put("required", JSONArray().put("simulated_messages").put("simulated_moments").put("simulated_tweets"))
            put("_schema_name", schemaName)
        }
    }

    fun contactMetadataSchema(schemaName: String = "cosmos_contact_metadata"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("nickname", JSONObject().apply { put("type", "string") })
                put("signature", JSONObject().apply { put("type", "string") })
            })
            put("required", JSONArray().put("nickname").put("signature"))
            put("_schema_name", schemaName)
        }
    }

    fun twitterProfileMetadataSchema(schemaName: String = "cosmos_twitter_profile_metadata"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("nickname", JSONObject().apply { put("type", "string") })
                put("username", JSONObject().apply { put("type", "string") })
                put("bio", JSONObject().apply { put("type", "string") })
            })
            put("required", JSONArray().put("nickname").put("username").put("bio"))
            put("_schema_name", schemaName)
        }
    }

    fun characterNamesSchema(schemaName: String = "cosmos_character_names"): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("names", JSONArrayItemsSchema(JSONObject().apply {
                    put("type", "string")
                }))
            })
            put("required", JSONArray().put("names"))
            put("_schema_name", schemaName)
        }
    }

    private fun socialPostSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", true)
            put("properties", JSONObject().apply {
                put("character_id", JSONObject().apply { put("type", "string") })
                put("content", JSONObject().apply { put("type", "string") })
                put("has_image", JSONObject().apply { put("type", "boolean") })
                put("image_description", JSONObject().apply { put("type", "string") })
                put("has_video", JSONObject().apply { put("type", "boolean") })
                put("video_description", JSONObject().apply { put("type", "string") })
                put("time", JSONObject().apply { put("type", "string") })
            })
        }
    }

    private fun JSONArrayItemsSchema(itemSchema: JSONObject): JSONObject {
        return JSONObject().apply {
            put("type", "array")
            put("items", itemSchema)
        }
    }
}
