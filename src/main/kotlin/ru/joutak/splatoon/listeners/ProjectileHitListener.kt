package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

class ProjectileHitListener : Listener {

    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent) {
        val entity = event.entity
        val block = event.hitBlock
        val hitEntity = event.hitEntity
        val blockface = event.hitBlockFace

        if (entity.type != EntityType.SNOWBALL) return
        if (!entity.hasMetadata("paintKey")) return

        val shooterUuid = getShooterUuid(entity.getMetadata("shooterId").firstOrNull()?.asString())
        val shooter = if (shooterUuid != null) Bukkit.getPlayer(shooterUuid) else null
        if (shooter == null) return

        val shooterGame = GameManager.playerGame[shooter.uniqueId] ?: return

        val paintTeam = entity.getMetadata("paintTeam").firstOrNull()?.asInt()
            ?: (shooterGame.commands[shooter.uniqueId] ?: return)

        val radius = if (entity.hasMetadata("bombKey")) 5.0 else 1.5

        if (hitEntity != null && hitEntity is Player) {
            val victim = hitEntity
            val victimGame = GameManager.playerGame[victim.uniqueId]
            if (victimGame != null && victimGame == shooterGame) {
                val shooterTeam = shooterGame.commands[shooter.uniqueId]
                val victimTeam = shooterGame.commands[victim.uniqueId]
                if (shooterTeam != null && victimTeam != null && shooterTeam != victimTeam) {
                    if (shooterGame.isJammerActive(shooter.uniqueId)) {
                        shooterGame.applyAmmoOverride(victim.uniqueId, shooterTeam, 5_000)
                    }
                    shooterGame.kills[shooter.uniqueId] = (shooterGame.kills[shooter.uniqueId] ?: 0) + 1
                    splatAndRespawn(victim, shooterGame.worldName)
                }
            }
            explosivePaint(radius, victim.location, entity.world, shooterGame, shooter.uniqueId, paintTeam)
        } else {
            if (block == null || blockface == null) return
            explosivePaint(radius, block.getRelative(blockface).location, entity.world, shooterGame, shooter.uniqueId, paintTeam)
        }
    }

    private fun splatAndRespawn(player: Player, worldName: String) {
        val w = Bukkit.getWorld(worldName) ?: return
        val spawn = w.spawnLocation

        player.activePotionEffects.forEach { e -> player.removePotionEffect(e.type) }
        player.teleport(spawn)
        player.velocity = player.velocity.zero()
        player.fireTicks = 0
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f

        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 40, 10, false, false, false))
        player.noDamageTicks = 40
    }

    private fun getShooterUuid(str: String?): UUID? {
        if (str.isNullOrBlank()) return null
        return try {
            UUID.fromString(str)
        } catch (_: Exception) {
            null
        }
    }

    private fun explosivePaint(r: Double, location: org.bukkit.Location, world: World, game: ru.joutak.splatoon.scripts.Game, shooterId: UUID, paintTeam: Int) {
        val blocks = mutableListOf<Block>()
        for (x in roundFromZero(location.x - r)..roundFromZero(location.x + r)) {
            for (y in roundFromZero(location.y - r)..roundFromZero(location.y + r)) {
                for (z in roundFromZero(location.z - r)..roundFromZero(location.z + r)) {
                    val b = world.getBlockAt(x, y, z)
                    if (b.type != Material.AIR && b.location.distance(location) <= r) blocks.add(b)
                }
            }
        }

        val paintable = setOf(
            Material.WHITE_CONCRETE,
            Material.RED_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.BLUE_CONCRETE
        )

        val matToTeam = mutableMapOf<Material, Int>()
        game.commandColors.forEach { (team, mat) -> matToTeam[mat] = team }

        val newMat = game.commandColors[paintTeam] ?: Material.WHITE_CONCRETE

        for (b in blocks) {
            if (!paintable.contains(b.type)) continue
            if (b.type == newMat) continue

            val oldTeam = matToTeam[b.type]
            if (oldTeam != null) {
                game.paintedCommand[oldTeam] = (game.paintedCommand[oldTeam] ?: 0) - 1
            }

            b.type = newMat

            game.paintedPerson[shooterId] = (game.paintedPerson[shooterId] ?: 0) + 1
            game.paintedCommand[paintTeam] = (game.paintedCommand[paintTeam] ?: 0) + 1
        }
    }

    private fun roundFromZero(n: Double): Int {
        return when {
            n > 0 -> ceil(n).toInt()
            n < 0 -> floor(n).toInt()
            else -> n.toInt()
        }
    }
}
