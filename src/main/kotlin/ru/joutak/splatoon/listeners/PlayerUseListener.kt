package ru.joutak.splatoon.listeners

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffectType

class PlayerUseListener : Listener {
    @EventHandler
    fun playerUseItemEvent(event: PlayerInteractEvent){
        val player = event.player
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return

        if (player.inventory.itemInMainHand.type == Material.GOLDEN_SHOVEL)
            player.world.spawnEntity(Location(player.world, player.eyeLocation.x, player.eyeLocation.y - 0.1, player.eyeLocation.z).add(player.location.direction), EntityType.SNOWBALL).velocity = player.location.direction.multiply(1.4)
    }
}