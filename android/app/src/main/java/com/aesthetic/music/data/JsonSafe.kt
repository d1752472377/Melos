package com.aesthetic.music.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Gson 的安全取值辅助。
 *
 * 为什么需要它们：Gson 的 `JsonObject.getAsJsonArray(name)` 实现是
 * `return (JsonArray) members.get(name);` —— **完全没有类型检查**。
 * 平台接口的字段类型并不稳定（同一个 `album` 字段有时是数组、有时是对象），
 * 一旦形状变了就抛 `ClassCastException: JsonObject cannot be cast to JsonArray`，
 * 而 R8 混淆后堆栈里只剩 `kj1 cannot be cast to li1`，根本看不出是哪儿。
 *
 * 空数组而不是 null：调用方几乎都是 `?.forEach` / `?.map`，
 * 返回空数组可以省掉一层判空，也不会改变"取不到就当没有"的语义。
 */
fun JsonObject.arrayOrEmpty(name: String): JsonArray =
    (get(name) as? JsonArray) ?: JsonArray()

fun JsonObject.objOrNull(name: String): JsonObject? =
    get(name) as? JsonObject

fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

/**
 * 只在确实是字符串/数字时取值。
 *
 * `JsonElement.getAsString()` 遇到对象或数组会抛 `UnsupportedOperationException`，
 * 和上面的强转是同一类"字段形状变了就炸"的问题。
 */
fun JsonObject.strOrNull(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

fun JsonElement?.strOrNull(): String? = this?.takeIf { it.isJsonPrimitive }?.asString

fun JsonObject.intOrNull(name: String): Int? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isNumber }?.asInt
