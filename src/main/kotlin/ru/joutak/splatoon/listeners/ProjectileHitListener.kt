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
        if (!projectile.hasMetadata("paintKey")) return

        val shooterId = getShooterUuid(projectile.getMetadata("shooterId").firstOrNull()?.asString()) ?: return
        val shooter = Bukkit.getPlayer(shooterId) ?: return

        val paintTeam = projectile.getMetadata("paintTeam").firstOrNull()?.asInt() ?: return
        val baseTeam = projectile.getMetadata("baseTeam").firstOrNull()?.asInt()

        val isBomb = projectile.hasMetadata("bombKey")
        val radius = if (isBomb) 5.0 else 1.5

        val hitBlock = event.hitBlock
        val hitFace = event.hitBlockFace
        val hitEntity = event.hitEntity

        val paintCenter = when {
            hitEntity != null -> hitEntity.location
            hitBlock != null && hitFace != null -> hitBlock.getRelative(hitFace).location
            else -> null
        } ?: return

        val game = GameManager.playerGame[shooter.uniqueId]

        if (game == null) {
            if (!shooter.hasPermission("splatoon.admin")) return
            if (hitEntity is Player) {
                explosivePaintAdmin(radius, hitEntity.location, projectile.world, paintTeam)
            } else {
                explosivePaintAdmin(radius, paintCenter, projectile.world, paintTeam)
            }
            return
        }

        val shooterTeam = game.commands[shooter.uniqueId] ?: return
        val shooterBaseTeam = baseTeam ?: shooterTeam

        if (isBomb) {
            val protectedVictims = mutableSetOf<UUID>()
            val excludeBlocks = mutableSetOf<Block>()

            projectile.world.getNearbyEntities(paintCenter, radius, radius, radius).forEach { e: Entity ->
                if (e !is Player) return@forEach
                if (GameManager.playerGame[e.uniqueId] != game) return@forEach

                val victimTeam = game.commands[e.uniqueId] ?: return@forEach
                if (victimTeam == shooterTeam) return@forEach

                val ownBlocks = inkProtectedBlocks(e, game)
                val protected = isSpawnSafe(e, game) || e.hasPotionEffect(PotionEffectType.INVISIBILITY) || ownBlocks.isNotEmpty()
                if (protected) {
                    protectedVictims.add(e.uniqueId)
                    excludeBlocks.addAll(ownBlocks)
                }
            }

            explosivePaint(radius, paintCenter, projectile.world, game, shooterId, paintTeam, shooterBaseTeam, excludeBlocks)

            projectile.world.getNearbyEntities(paintCenter, radius, radius, radius).forEach { e: Entity ->
                if (e !is Player) return@forEach
                if (GameManager.playerGame[e.uniqueId] != game) return@forEach

                val victimTeam = game.commands[e.uniqueId] ?: return@forEach
                if (victimTeam == shooterTeam) return@forEach
                if (protectedVictims.contains(e.uniqueId)) return@forEach

                applyInkDamage(e, game, shooterId, shooterTeam, paintTeam, shooterBaseTeam, isBomb = true)
            }
            return
        }

        if (hitEntity is Player) {
            val victim = hitEntity
            val victimGame = GameManager.playerGame[victim.uniqueId]
            if (victimGame != null && victimGame == game) {
                val victimTeam = game.commands[victim.uniqueId]
                if (victimTeam != null && victimTeam != shooterTeam) {
                    val ownBlocks = inkProtectedBlocks(victim, game)
                    val protected = isSpawnSafe(victim, game) || victim.hasPotionEffect(PotionEffectType.INVISIBILITY) || ownBlocks.isNotEmpty()

                    explosivePaint(radius, victim.location, projectile.world, game, shooterId, paintTeam, shooterBaseTeam, ownBlocks)

                    if (!protected) {
                        applyInkDamage(victim, game, shooterId, shooterTeam, paintTeam, shooterBaseTeam, isBomb = false)
                    }
                    return
                }
            }

            explosivePaint(radius, victim.location, projectile.world, game, shooterId, paintTeam, shooterBaseTeam)
            return
        }

        explosivePaint(radius, paintCenter, projectile.world, game, shooterId, paintTeam, shooterBaseTeam)
    }

    private fun applyInkDamage(
        victim: Player,
        game: Game,
        shooterId: UUID,
        shooterTeam: Int,
        paintTeam: Int,
        shooterBaseTeam: Int,
        isBomb: Boolean
    ) {
        val victimTeam = game.commands[victim.uniqueId] ?: return
        if (victimTeam == shooterTeam) return

        val current = game.inkHp[victim.uniqueId] ?: game.maxInkHp
        val next = current - 1

        if (next > 0) {
            game.inkHp[victim.uniqueId] = next
            return
        }

        game.kills[shooterId] = (game.kills[shooterId] ?: 0) + 1

        val killRadius = if (isBomb) 4.0 else 3.0
        explosivePaint(killRadius, victim.location, victim.world, game, shooterId, paintTeam, shooterBaseTeam)

        splatAndRespawn(victim, game)
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

    private fun inkProtectedBlocks(player: Player, game: Game): Set<Block> {
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return emptySet()

        val team = game.commands[player.uniqueId] ?: return emptySet()
        val mat = game.commandColors[team] ?: return emptySet()

        val loc = player.location
        val y = loc.blockY - 1

        val blocks = mutableSetOf<Block>()
        for (dx in listOf(-0.25, 0.0, 0.25)) {
            for (dz in listOf(-0.25, 0.0, 0.25)) {
                val b = loc.world.getBlockAt(
                    floor(loc.x + dx).toInt(),
                    y,
                    floor(loc.z + dz).toInt()
                )
                if (b.type == mat) blocks.add(b)
            }
        }
        return blocks
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

        game.inkHp[player.uniqueId] = game.maxInkHp
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
        shooterBaseTeam: Int,
        excludeBlocks: Set<Block> = emptySet()
    ) {
        val exclude = HashSet<String>()
        excludeBlocks.forEach { b -> exclude.add("${b.x},${b.y},${b.z}") }

        val blocks = mutableListOf<Block>()
        for (x in roundFromZero(location.x - r)..roundFromZero(location.x + r)) {
            for (y in roundFromZero(location.y - r)..roundFromZero(location.y + r)) {
                for (z in roundFromZero(location.z - r)..roundFromZero(location.z + r)) {
                    if (exclude.contains("$x,$y,$z")) continue
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

            val delta = when {
                paintTeam == shooterBaseTeam && oldTeam != shooterBaseTeam -> 1
                paintTeam != shooterBaseTeam && oldTeam == shooterBaseTeam -> -1
                else -> 0
            }
            if (delta != 0) {
                game.paintedPerson[shooterId] = (game.paintedPerson[shooterId] ?: 0) + delta
            }
            game.paintedCommand[paintTeam] = (game.paintedCommand[paintTeam] ?: 0) + 1
        }
    }

    private fun explosivePaintAdmin(r: Double, location: org.bukkit.Location, world: World, paintTeam: Int) {
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

        val teamColors = mapOf(
            0 to Material.RED_CONCRETE,
            1 to Material.YELLOW_CONCRETE,
            2 to Material.GREEN_CONCRETE,
            3 to Material.BLUE_CONCRETE
        )

        val newMat = teamColors[paintTeam] ?: Material.WHITE_CONCRETE

        for (b in blocks) {
            if (!paintable.contains(b.type)) continue
            if (b.type == newMat) continue
            b.type = newMat
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
