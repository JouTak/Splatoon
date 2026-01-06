package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID

class PlayerSessionListener : Listener {
    @EventHandler
    fun playerQuitEvent(event: PlayerQuitEvent) {
        handleDisconnect(event.player.uniqueId)
    }

    @EventHandler
    fun playerKickEvent(event: PlayerKickEvent) {
        handleDisconnect(event.player.uniqueId)
    }

    private fun handleDisconnect(uuid: UUID) {
        if (PlayerToggleSneakListener.tasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(PlayerToggleSneakListener.tasks[uuid]!!)
            PlayerToggleSneakListener.tasks.remove(uuid)
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

            GameManager.removePlayerFromGame(uuid)
            GameManager.sendToLobby(player)
        })
    }
}
