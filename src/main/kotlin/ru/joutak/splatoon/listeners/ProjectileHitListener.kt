package ru.joutak.splatoon.listeners

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import kotlin.math.floor
import kotlin.math.ceil

class ProjectileHitListener : Listener {

    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent) {
        val entity = event.entity
        val block = event.hitBlock
        val blockface = event.hitBlockFace

        if (block == null || blockface == null || entity.type != EntityType.SNOWBALL) return
        explosivePaint(1.5, block.getRelative(blockface).location, entity.world)
    }

    fun explosivePaint(r: Double, location: org.bukkit.Location, world: World) {
        val blocks = mutableListOf<Block>()
        for (x in roundFromZero(location.x - r)..roundFromZero(location.x + r)) {
            for (y in roundFromZero(location.y - r)..roundFromZero(location.y + r)) {
                for (z in roundFromZero(location.z - r)..roundFromZero(location.z + r)) {
                    val b = world.getBlockAt(x, y, z)
                    if (b.type != Material.AIR && b.location.distance(location) <= r) blocks.add(b)
                }
            }
        }
        for (b in blocks) {
            if (b.type != Material.AIR) b.type = Material.GREEN_CONCRETE
        }
    }

    fun roundFromZero(n: Double): Int {
        return when {
            n > 0 -> ceil(n).toInt()
            n < 0 -> floor(n).toInt()
            else -> n.toInt()
        }
    }
}