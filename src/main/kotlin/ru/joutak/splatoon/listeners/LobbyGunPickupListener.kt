package ru.joutak.splatoon.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import ru.joutak.splatoon.scripts.GameManager
import ru.joutak.splatoon.scripts.LobbyGunStand

class LobbyGunPickupListener : Listener {

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player

        if(!GameManager.isLobbyWorld(player.world)) return

        if (GameManager.playerGame.containsKey(player.uniqueId  )) return

        for (location in LobbyGunStand.getLocations()) {
            if (player.location.distance(location) <= 1.5) {
                LobbyGunStand.tryPickup(player, location)
                break
            }
        }
    }
}