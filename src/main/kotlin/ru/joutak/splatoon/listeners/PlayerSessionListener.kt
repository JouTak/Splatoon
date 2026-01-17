package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.scripts.GameManager

class PlayerSessionListener : Listener {
    @EventHandler
    fun playerQuitEvent(event: PlayerQuitEvent) {
        handleDisconnect(event.player)
    }

    @EventHandler
    fun playerKickEvent(event: PlayerKickEvent) {
        handleDisconnect(event.player)
    }

    private fun handleDisconnect(player: Player) {
        val uuid = player.uniqueId

        if (PlayerToggleSneakListener.tasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(PlayerToggleSneakListener.tasks[uuid]!!)
            PlayerToggleSneakListener.tasks.remove(uuid)
        }

        // Admin spectate mode cleanup.
        val spectateGame = GameManager.getSpectatingGame(player)
        if (spectateGame != null) {
            runCatching { spectateGame.removeSpectator(player, silent = true, forceLobby = true) }
        } else {
            GameManager.clearSpectating(uuid)
        }

        GameManager.removePlayerFromGame(uuid)
    }

    @EventHandler
    fun playerJoinEvent(event: PlayerJoinEvent) {
        val player = event.player
        Bukkit.getScheduler().runTask(SplatoonPlugin.instance, Runnable {
            val uuid = player.uniqueId

            if (PlayerToggleSneakListener.tasks.containsKey(uuid)) {
                Bukkit.getScheduler().cancelTask(PlayerToggleSneakListener.tasks[uuid]!!)
                PlayerToggleSneakListener.tasks.remove(uuid)
            }

            // If the player had a stale spectate state (e.g. crash/reload), drop it.
            GameManager.clearSpectating(uuid)
            GameManager.removePlayerFromGame(uuid)
            GameManager.sendToLobby(player)
        })
    }
}
