package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit.getPlayer
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import ru.joutak.splatoon.scripts.GameManager
import kotlin.math.floor
import kotlin.math.ceil

class ProjectileHitListener : Listener {

    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent) {
        val entity = event.entity
        val block = event.hitBlock
        val hitEntity = event.hitEntity
        val blockface = event.hitBlockFace

        if (entity.type != EntityType.SNOWBALL) return
        val radius = if (entity.hasMetadata("bombKey")) 5.0 else 1.5
        if (entity.hasMetadata("paintKey")) {
            val shooter = entity.getMetadata("shooter").firstOrNull()?.asString().toString()
            if (hitEntity != null && hitEntity is Player) {
                val snowballDirection = entity.velocity.normalize()
                hitEntity.velocity = snowballDirection.multiply(1.5)
                explosivePaint(radius, hitEntity.location, entity.world, shooter)
            }

            if (block == null || blockface == null) return
            explosivePaint(radius, block.getRelative(blockface).location, entity.world, shooter)
        }
    }

    fun explosivePaint(r: Double, location: org.bukkit.Location, world: World, shooter: String) {
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
            val shooterId = getPlayer(shooter)!!.uniqueId
            val shooterGame = GameManager.playerGame[shooterId]!!

            if (b.type != Material.AIR && b.type != shooterGame.commandColors[shooterGame.commands[shooterId]]) {
                b.type = shooterGame.commandColors[shooterGame.commands[shooterId]] ?: Material.WHITE_CONCRETE
                shooterGame.paintedPerson[shooterId] =
                    (shooterGame.paintedPerson[shooterId] ?: 0) + 1
                shooterGame.paintedCommand[shooterGame.commands[shooterId] ?: 0] =
                    (shooterGame.paintedCommand[shooterGame.commands[shooterId]] ?: 0) + 1
            }
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