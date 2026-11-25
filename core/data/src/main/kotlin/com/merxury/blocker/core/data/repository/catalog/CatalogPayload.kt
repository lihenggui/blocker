// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import com.merxury.blocker.core.database.catalog.CatalogComponentEntity
import com.merxury.blocker.core.database.catalog.CatalogComponentTextEntity
import com.merxury.blocker.core.database.catalog.CatalogComponentWithText
import com.merxury.blocker.core.database.catalog.CatalogRuleEntity
import com.merxury.blocker.core.database.catalog.CatalogRuleTextEntity
import com.merxury.blocker.core.database.catalog.CatalogRuleWithText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

@OptIn(ExperimentalSerializationApi::class)
internal fun readCatalogRules(stream: InputStream, files: Set<String>): List<CatalogRuleWithText> {
    val root = Json.decodeFromStream<JsonObject>(stream)
    root.fields("rules")
    val rules = root.array("rules").map { readRule(it.catalogObject("rule"), files) }
    verifyCatalog(rules.zipWithNext().all { (left, right) -> left.rule.id < right.rule.id }) {
        "Rule IDs must be sorted and unique"
    }
    return rules
}

private fun readRule(value: JsonObject, files: Set<String>): CatalogRuleWithText {
    value.fields("id", "icon", "website", "keywords", "safeToBlock", "contributors", "i18n")
    val id = value.positiveInteger("id")
    val icon = value.optionalString("icon")
    verifyCatalog(icon == null || (isIconPath(icon) && icon in files)) { "Rule $id references an unlisted icon $icon" }
    val rule = CatalogRuleEntity(
        id = id,
        icon = icon,
        website = value.optionalString("website"),
        keywords = value.strings("keywords"),
        safeToBlock = value["safeToBlock"]?.let { value.boolean("safeToBlock") },
        contributors = value.strings("contributors"),
    )
    val texts = value.localized { language, text ->
        text.fields("name", "company", "description", "sideEffect")
        val name = text.optionalString("name")
        verifyCatalog(language != "en" || !name.isNullOrEmpty()) { "Rule $id requires a nonempty en.name" }
        CatalogRuleTextEntity(id, language, name, text.optionalString("company"), text.optionalString("description"), text.optionalString("sideEffect"))
    }
    return CatalogRuleWithText(rule, texts)
}

internal fun readCatalogComponent(line: String, ruleIds: Set<Long>): CatalogComponentWithText {
    val value = Json.parseToJsonElement(line).catalogObject("component")
    value.fields("name", "sdkRuleId", "recommendToBlock", "contributor", "updatedAt", "i18n")
    val name = value.string("name")
    verifyCatalog('.' in name && name.split('.').all { it.isNotEmpty() }) { "Expected a fully qualified component name" }
    val sdkRuleId = value["sdkRuleId"]?.let { value.positiveInteger("sdkRuleId") }
    verifyCatalog(sdkRuleId == null || sdkRuleId in ruleIds) { "Component $name references unknown sdkRuleId $sdkRuleId" }
    val updatedAt = value.optionalString("updatedAt")?.also { validateInstant(it, "updatedAt") }
    val component = CatalogComponentEntity(name, sdkRuleId, value.boolean("recommendToBlock"), value.optionalString("contributor"), updatedAt)
    val texts = value.localized { language, text ->
        text.fields("description", "disableEffect")
        val description = text.optionalString("description")
        verifyCatalog(language != "en" || description != null) { "Component $name requires en.description" }
        CatalogComponentTextEntity(name, language, description, text.optionalString("disableEffect"))
    }
    return CatalogComponentWithText(component, texts)
}

private inline fun <T> JsonObject.localized(read: (String, JsonObject) -> T): List<T> {
    val localized = this["i18n"].catalogObject("i18n")
    localized.fields("en", "zh-CN")
    verifyCatalog("en" in localized) { "Missing en text" }
    return localized.map { (language, value) ->
        val text = value.catalogObject(language)
        verifyCatalog(text.isNotEmpty()) { "Empty $language text object" }
        read(language, text)
    }
}
