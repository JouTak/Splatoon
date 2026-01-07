package ru.joutak.splatoon.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

class AdminGiveItemCommand(private val plugin: Plugin, private val type: Type) : CommandExecutor {

    enum class Type {
        GUN,
        BOMB,
        BACILLUS
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only for players")
            return true
        }

        if (!sender.isOp && !sender.hasPermission("splatoon.admin")) {
            sender.sendMessage("Нет прав")
            return true
        }

        val item = when (type) {
            Type.GUN -> createItem(Material.GOLDEN_SHOVEL, "Сплат-пушка", "splatGun")
            Type.BOMB -> createItem(Material.GOLDEN_AXE, "Сплат-бомба", "Bomb")
            Type.BACILLUS -> createItem(Material.AMETHYST_SHARD, "Бацилла", "Bacillus")
        }

        sender.inventory.addItem(item)
        return true
    }

    private fun createItem(material: Material, name: String, key: String): ItemStack {
        val item = ItemStack(material, 1)
        val meta = item.itemMeta
        meta.displayName(Component.text(name).color(TextColor.color(0xFF55FF)))
        meta.persistentDataContainer.set(NamespacedKey(plugin, key), PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        return item
    }
}
