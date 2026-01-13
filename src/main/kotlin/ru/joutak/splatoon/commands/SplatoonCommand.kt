package ru.joutak.splatoon.commands

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import ru.joutak.splatoon.SplatoonPlugin

class SplatoonCommand(private val plugin: SplatoonPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§cИспользование: /splatoon get <gun|bomb|bacillus>")
            return true
        }

        if (args[0].equals("get", ignoreCase = true)) {
            if (sender !is Player) {
                sender.sendMessage("§cТолько для игроков")
                return true
            }
            if (!sender.hasPermission("splatoon.admin")) {
                sender.sendMessage("§cНедостаточно прав")
                return true
            }
            if (args.size < 2) {
                sender.sendMessage("§cИспользование: /splatoon get <gun|bomb|bacillus>")
                return true
            }

            when (args[1].lowercase()) {
                "gun" -> {
                    sender.inventory.addItem(AdminItems.gun())
                    sender.sendMessage("§aВыдано: Сплат-пушка")
                }

                "bomb" -> {
                    sender.inventory.addItem(AdminItems.bomb())
                    sender.sendMessage("§aВыдано: Сплат-бомба")
                }

                "bacillus" -> {
                    sender.inventory.addItem(AdminItems.bacillus())
                    sender.sendMessage("§aВыдано: Бацилла")
                }

                else -> {
                    sender.sendMessage("§cНеизвестный предмет. Доступно: gun, bomb, bacillus")
                }
            }
            return true
        }

        sender.sendMessage("§cНеизвестная команда")
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            return listOf("get").filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        if (args.size == 2 && args[0].equals("get", ignoreCase = true)) {
            return listOf("gun", "bomb", "bacillus").filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        return mutableListOf()
    }

    private fun hasAmmo(player: Player): Boolean {
        val key = NamespacedKey(plugin, "splatAmmo")
        return player.inventory.contents.any { st ->
            st != null && st.type == Material.ARROW && st.hasItemMeta() && st.itemMeta.persistentDataContainer.has(
                key,
                PersistentDataType.BOOLEAN
            )
        }
    }
}
