package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
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
        val projectile = event.entity
        if (projectile.type != EntityType.SNOWBALL) return

        val shooterId = getShooterUuid(projectile.getMetadata("shooterId").firstOrNull()?.asString()) ?: return
        val shooter = Bukkit.getPlayer(shooterId) ?: return

        val game = GameManager.playerGame[shooter.uniqueId] ?: return
        val shooterTeam = game.commands[shooter.uniqueId] ?: return

        if (!projectile.hasMetadata("paintKey")) return

        val hitBlock = event.hitBlock
        val hitFace = event.hitBlockFace
        val hitEntity = event.hitEntity

        val paintTeam = projectile.getMetadata("paintTeam").firstOrNull()?.asInt() ?: (game.commands[shooter.uniqueId] ?: return)
        val isBomb = projectile.hasMetadata("bombKey")
        val radius = if (isBomb) 5.0 else 1.5

        val paintCenter = when {
            hitEntity != null -> hitEntity.location
            hitBlock != null && hitFace != null -> hitBlock.getRelative(hitFace).location
            else -> null
        } ?: return

        val protectedBeforePaint = if (isBomb) {
            collectProtectedBeforePaint(projectile.world, paintCenter, radius, game, shooterTeam)
        } else {
            emptySet()
        }

        if (hitEntity is Player) {
            val victim = hitEntity
            if (GameManager.playerGame[victim.uniqueId] == game) {
                val victimTeam = game.commands[victim.uniqueId]
                if (victimTeam != null && victimTeam != shooterTeam) {
                    val onOwn = isOnOwnColor(victim, game, victimTeam)
                    val protected = isSpawnSafe(victim, game) || onOwn

                    val exclude = if (onOwn) victim.location.clone().subtract(0.0, 1.0, 0.0).block else null
                    explosivePaint(radius, victim.location, projectile.world, game, shooterId, paintTeam, exclude)

                    if (!isBomb) {
                        if (!protected) {
                            game.kills[shooterId] = (game.kills[shooterId] ?: 0) + 1
                            splatAndRespawn(victim, game)
                        }
                    } else {
                        applyBombAoE(projectile.world, victim.location, radius, game, shooterId, shooterTeam, protectedBeforePaint)
                    }
                    return
                }
            }

            explosivePaint(radius, victim.location, projectile.world, game, shooterId, paintTeam)
            if (isBomb) {
                applyBombAoE(projectile.world, victim.location, radius, game, shooterId, shooterTeam, protectedBeforePaint)
            }
            return
        }

        explosivePaint(radius, paintCenter, projectile.world, game, shooterId, paintTeam)
        if (isBomb) {
            applyBombAoE(projectile.world, paintCenter, radius, game, shooterId, shooterTeam, protectedBeforePaint)
        }
    }

    private fun collectProtectedBeforePaint(world: World, center: org.bukkit.Location, radius: Double, game: Game, shooterTeam: Int): Set<UUID> {
        return world.getNearbyEntities(center, radius, radius, radius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { GameManager.playerGame[it.uniqueId] == game }
            .mapNotNull { p ->
                val team = game.commands[p.uniqueId] ?: return@mapNotNull null
                if (team == shooterTeam) return@mapNotNull null
                val protected = isSpawnSafe(p, game) || isOnOwnColor(p, game, team)
                if (protected) p.uniqueId else null
            }
            .toSet()
    }

    private fun applyBombAoE(
        world: World,
        center: org.bukkit.Location,
        radius: Double,
        game: Game,
        shooterId: UUID,
        shooterTeam: Int,
        protectedBeforePaint: Set<UUID>
    ) {
        world.getNearbyEntities(center, radius, radius, radius).forEach { e: Entity ->
            if (e !is Player) return@forEach
            if (GameManager.playerGame[e.uniqueId] != game) return@forEach

            val victimTeam = game.commands[e.uniqueId] ?: return@forEach
            if (victimTeam == shooterTeam) return@forEach
            if (protectedBeforePaint.contains(e.uniqueId)) return@forEach

            game.kills[shooterId] = (game.kills[shooterId] ?: 0) + 1
            splatAndRespawn(e, game)
        }
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

    private fun isOnOwnColor(player: Player, game: Game, team: Int): Boolean {
        val ownMat = game.commandColors[team] ?: return false
        val under = player.location.clone().subtract(0.0, 1.0, 0.0).block
        return under.type == ownMat
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

    private fun explosivePaint(
        r: Double,
        location: org.bukkit.Location,
        world: World,
        game: Game,
        shooterId: UUID,
        paintTeam: Int,
        excludeBlock: Block? = null
    ) {
        val excludeX = excludeBlock?.x
        val excludeY = excludeBlock?.y
        val excludeZ = excludeBlock?.z

        val blocks = mutableListOf<Block>()
        for (x in roundFromZero(location.x - r)..roundFromZero(location.x + r)) {
            for (y in roundFromZero(location.y - r)..roundFromZero(location.y + r)) {
                for (z in roundFromZero(location.z - r)..roundFromZero(location.z + r)) {
                    if (excludeX != null && x == excludeX && y == excludeY && z == excludeZ) continue
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
