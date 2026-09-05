package com.local.dailyautomation.domain

import com.google.gson.JsonParser

class ModuleCatalog(modules: List<ModuleDescriptor>) {

    private val modules = modules.toList().also(::validate)

    fun all(): List<ModuleDescriptor> = modules.sortedBy(ModuleDescriptor::sortOrder)

    fun enabledInRunOrder(): List<ModuleDescriptor> = all().filter(ModuleDescriptor::enabled)

    fun requireById(id: String): ModuleDescriptor = modules.single { it.id == id }

    companion object {
        fun load(json: String): ModuleCatalog {
            val root = JsonParser.parseString(json)
            require(root.isJsonArray) { "Module manifest must be a JSON array" }
            val modules = root.asJsonArray.map { element ->
                require(element.isJsonObject) { "Each module must be a JSON object" }
                val item = element.asJsonObject
                ModuleDescriptor(
                    id = item.requiredString("id"),
                    displayName = item.requiredString("displayName"),
                    targetPackage = item.requiredString("targetPackage"),
                    enabled = item.requiredBoolean("enabled"),
                    sortOrder = item.requiredInt("sortOrder"),
                    script = item.requiredString("script"),
                )
            }
            return ModuleCatalog(modules)
        }

        private fun validate(modules: List<ModuleDescriptor>) {
            require(modules.isNotEmpty()) { "Module catalog cannot be empty" }
            require(modules.map(ModuleDescriptor::id).distinct().size == modules.size) {
                "Module IDs must be unique"
            }
            require(modules.map(ModuleDescriptor::sortOrder).distinct().size == modules.size) {
                "Module sort orders must be unique"
            }
            modules.forEach { module ->
                require(module.id.isNotBlank()) { "Module ID cannot be blank" }
                require(module.displayName.isNotBlank()) { "Module display name cannot be blank" }
                require(module.targetPackage.isNotBlank()) { "Module target package cannot be blank" }
                require(module.sortOrder >= 0) { "Module sort order cannot be negative" }
                require(module.script.startsWith("modules/") && module.script.endsWith(".js")) {
                    "Module script must be a project-relative JavaScript path"
                }
            }
        }
    }
}

private fun com.google.gson.JsonObject.requiredString(name: String): String {
    require(has(name) && get(name).isJsonPrimitive && get(name).asJsonPrimitive.isString) {
        "Missing or invalid string field: $name"
    }
    return get(name).asString
}

private fun com.google.gson.JsonObject.requiredBoolean(name: String): Boolean {
    require(has(name) && get(name).isJsonPrimitive && get(name).asJsonPrimitive.isBoolean) {
        "Missing or invalid boolean field: $name"
    }
    return get(name).asBoolean
}

private fun com.google.gson.JsonObject.requiredInt(name: String): Int {
    require(has(name) && get(name).isJsonPrimitive && get(name).asJsonPrimitive.isNumber) {
        "Missing or invalid integer field: $name"
    }
    return get(name).asInt
}
