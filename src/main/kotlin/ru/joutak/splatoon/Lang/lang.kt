package ru.joutak.splatoon.lang

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object Lang {

    private lateinit var config: FileConfiguration
    private val serializer = LegacyComponentSerializer.legacySection()

    fun load(plugin: JavaPlugin) {
        val file = File(plugin.dataFolder, "lang.yml")

        if (!file.exists()) {
            plugin.saveResource("lang.yml", false)
            plugin.logger.info("lang.yml created")
        }

        config = YamlConfiguration.loadConfiguration(file)
        plugin.logger.info("Lang loaded")
    }

    fun reload(plugin: JavaPlugin) {
        load(plugin)
        plugin.logger.info("Lang reloaded")
    }

    // 🔹 Просто строка
    fun get(path: String): String {
        return config.getString(path) ?: "§cMissing lang key: $path"
    }

    // 🔹 С плейсхолдерами
    fun get(path: String, vararg replacements: Pair<String, String>): String {
        var text = get(path)

        replacements.forEach { (key, value) ->
            text = text.replace("%$key%", value)
        }

        return text
    }

    // 🔹 Список строк
    fun getList(path: String): List<String> {
        return config.getStringList(path)
    }

    // 🔥 Сразу Component (ОЧЕНЬ удобно)
    fun component(path: String): Component {
        return serializer.deserialize(get(path))
    }

    fun component(path: String, vararg replacements: Pair<String, String>): Component {
        return serializer.deserialize(get(path, *replacements))
    }

    fun componentList(path: String): List<Component> {
        return getList(path).map { serializer.deserialize(it) }
    }
}