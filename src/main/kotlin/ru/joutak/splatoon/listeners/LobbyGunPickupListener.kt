package ru.joutak.splatoon.listeners

import org.bukkit.entity.ItemDisplay
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

        val nearbyEntities = player.getNearbyEntities(1.5,1.5,1.5)
        val nearbyDisplays = nearbyEntities.filterIsInstance<ItemDisplay>()

        for (display in nearbyDisplays) {
            if (display.scoreboardTags.contains("lobby_decoration")) continue

            if (LobbyGunStand.isGunStand(display.location)) {
                LobbyGunStand.tryPickup(player, display.location)
                break
            }
        }
    }
}