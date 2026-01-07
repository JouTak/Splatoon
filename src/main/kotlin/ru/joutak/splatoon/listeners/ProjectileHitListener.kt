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
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

class ProjectileHitListener : Listener {

    private val spawnProtectionRadius = 7.0
    private val spawnProtectionRadiusSq = spawnProtectionRadius * spawnProtectionRadius

    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent) {
        val entity = event.entity
        if (entity.type != EntityType.SNOWBALL) return

        val shooterUuid = getShooterUuid(entity.getMetadata("shooterId").firstOrNull()?.asString())
        val shooter = if (shooterUuid != null) Bukkit.getPlayer(shooterUuid) else null
        if (shooter == null) return

        val game = GameManager.playerGame[shooter.uniqueId] ?: return
        val shooterTeam = game.commands[shooter.uniqueId] ?: return


        if (!entity.hasMetadata("paintKey")) return

        val block = event.hitBlock
        val hitEntity = event.hitEntity
        val blockface = event.hitBlockFace

        val paintTeam = entity.getMetadata("paintTeam").firstOrNull()?.asInt()
            ?: (game.commands[shooter.uniqueId] ?: return)

        val isBomb = entity.hasMetadata("bombKey")
        val radius = if (isBomb) 5.0 else 1.5

        if (hitEntity != null && hitEntity is Player) {
            val victim = hitEntity
            val victimGame = GameManager.playerGame[victim.uniqueId]
            if (victimGame != null && victimGame == game) {
                val victimTeam = game.commands[victim.uniqueId]
                if (victimTeam != null && victimTeam != shooterTeam) {
                    if (!isSpawnSafe(victim, game)) {
                        if (!isInkSafe(victim, game)) {
                            game.kills[shooter.uniqueId] = (game.kills[shooter.uniqueId] ?: 0) + 1
                            splatAndRespawn(victim, game)
                        }
                    }
                }
            }
            explosivePaint(radius, victim.location, entity.world, game, shooter.uniqueId, paintTeam)
        } else {
            if (block == null || blockface == null) return
            explosivePaint(radius, block.getRelative(blockface).location, entity.world, game, shooter.uniqueId, paintTeam)
        }

        if (isBomb) {
            val center = if (hitEntity != null) hitEntity.location else (block?.getRelative(blockface!!)?.location)
            if (center != null) {
                entity.world.getNearbyEntities(center, radius, radius, radius).forEach { e ->
                    if (e is Player) {
                        val victimGame = GameManager.playerGame[e.uniqueId]
                        if (victimGame != null && victimGame == game) {
                            val victimTeam = game.commands[e.uniqueId]
                            if (victimTeam != null && victimTeam != shooterTeam) {
                                if (!isSpawnSafe(e, game)) {
                                    if (!isInkSafe(e, game)) {
                                        game.kills[shooter.uniqueId] = (game.kills[shooter.uniqueId] ?: 0) + 1
                                        splatAndRespawn(e, game)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isInkSafe(player: Player, game: Game): Boolean {
        val team = game.commands[player.uniqueId] ?: return false
        val mat = game.commandColors[team] ?: return false

        val feet = player.location.block.type
        val below = player.location.clone().subtract(0.0, 1.0, 0.0).block.type
        val onInk = feet == mat || below == mat

        return onInk
    }

    private fun isSpawnSafe(player: Player, game: Game): Boolean {
        val w = Bukkit.getWorld(game.worldName) ?: return false
        if (player.world.name != w.name) return false

        if (game.isSpawnProtectionActive(player.uniqueId)) return true

        val spawn = w.spawnLocation
        val dx = player.location.x - spawn.x
        val dy = player.location.y - spawn.y
        val dz = player.location.z - spawn.z
        val distSq = dx * dx + dy * dy + dz * dz
        return distSq <= spawnProtectionRadiusSq
    }

    private fun splatAndRespawn(player: Player, game: Game) {
        val w = Bukkit.getWorld(game.worldName) ?: return
        val spawn = w.spawnLocation

        player.activePotionEffects.forEach { e -> player.removePotionEffect(e.type) }
        player.teleport(spawn)
        player.velocity = player.velocity.zero()
        player.fireTicks = 0
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f

        game.setSpawnProtection(player.uniqueId, 4_000)
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 10, false, false, false))
        player.noDamageTicks = 60
    }

    private fun getShooterUuid(str: String?): UUID? {
        if (str.isNullOrBlank()) return null
        return try {
            UUID.fromString(str)
        } catch (_: Exception) {
            null
        }
    }

    private fun explosivePaint(r: Double, location: org.bukkit.Location, world: World, game: Game, shooterId: UUID, paintTeam: Int) {
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

        val baseTeam = game.commands[shooterId]
        val delta = if (baseTeam != null && baseTeam == paintTeam) 1 else -1

        for (b in blocks) {
            if (!paintable.contains(b.type)) continue
            if (b.type == newMat) continue

            val oldTeam = matToTeam[b.type]
            if (oldTeam != null) {
                game.paintedCommand[oldTeam] = (game.paintedCommand[oldTeam] ?: 0) - 1
            }

            b.type = newMat

            game.paintedPerson[shooterId] = (game.paintedPerson[shooterId] ?: 0) + delta
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
