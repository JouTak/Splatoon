package ru.joutak.splatoon.scripts

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.UUID

class AddNicksCommand : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§cУкажите ники через пробел: /splatoon Player1 Player2 Player3")
            return true
        }
        val playerIds = mutableListOf<UUID>()
        val nicks = args.toList()
        nicks.forEach { nick ->
            val player = Bukkit.getPlayer(nick)
            if (player != null) {
                playerIds.add(player.uniqueId)
            }
        }

        GameManager.createGame(playerIds)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        val currentArg = args.lastOrNull() ?: ""

        return Bukkit.getOnlinePlayers()
            .map { it.name }
            .filter { it.startsWith(currentArg, ignoreCase = true) }
            .sorted()
    }
}
