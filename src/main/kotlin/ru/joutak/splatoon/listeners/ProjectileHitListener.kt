package ru.joutak.splatoon.listeners

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

class ProjectileHitListener : Listener {

    private val paintable = setOf(
        Material.WHITE_CONCRETE,
        Material.RED_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.GREEN_CONCRETE,
        Material.BLUE_CONCRETE
    )

    @EventHandler
    fun onHit(event: ProjectileHitEvent) {
        val proj = event.entity
        if (proj !is Snowball) return
        if (!proj.hasMetadata("paintKey")) return

        val shooterUuid = getShooterUuid(proj) ?: return
        val game = GameManager.playerGame[shooterUuid]

        val hitPlayer = event.hitEntity as? Player
        val hitBlock = event.hitBlock

        if (hitPlayer != null && game != null) {
            handleDirectHitOnPlayer(proj, game, hitPlayer)
            return
        }

        if (hitBlock != null) {
            if (game != null) {
                handleProjectileHitOnBlock(proj, game, hitBlock)
            } else {
                handleAdminProjectileHitOnBlock(proj, hitBlock)
            }
        }
    }

    private fun getShooterUuid(snowball: Snowball): UUID? {
        val shooterMeta = snowball.getMetadata("shooterId").firstOrNull()?.asString() ?: return null
        return try {
            UUID.fromString(shooterMeta)
        } catch (_: Exception) {
            null
        }
    }

    private fun handleDirectHitOnPlayer(projectile: Snowball, game: Game, victim: Player) {
        val shooterUuid = getShooterUuid(projectile) ?: return
        val attacker = Bukkit.getPlayer(shooterUuid) ?: return
        if (GameManager.playerGame[victim.uniqueId] != game) return

        val attackerTeam = game.commands[attacker.uniqueId] ?: return
        val victimTeam = game.commands[victim.uniqueId] ?: return

        val settings = SplatoonPlugin.instance.settings
        if (isSpawnSafe(victim, game, settings.spawnProtection.radiusBlocks)) return

        val isBomb = projectile.hasMetadata("bombKey")
        val paintRadius = if (isBomb) settings.weapons.bomb.paintRadius else settings.weapons.gun.paintRadius

        if (attackerTeam == victimTeam) {
            paintAround(victim.location, projectile, game, paintRadius, excludeBlock = null)
            return
        }

        val victimStandingOnOwnInk = isStandingOnTeamInk(victim, victimTeam, game)

        if (!victimStandingOnOwnInk) {
            val hpLeft = game.damageInkHp(victim.uniqueId, 1)
            if (hpLeft <= 0) {
                handleDeath(projectile, game, attacker, victim)
                return
            } else {
                victim.playSound(
                    Sound.sound(Key.key("entity.arrow.hit_player"), Sound.Source.MASTER, 1.0f, 1.2f)
                )
            }
        }

        paintAround(victim.location, projectile, game, paintRadius, excludeBlock = if (victimStandingOnOwnInk) victim.location.block.getRelative(0, -1, 0) else null)
    }

    private fun handleProjectileHitOnBlock(projectile: Snowball, game: Game, hitBlock: Block) {
        val settings = SplatoonPlugin.instance.settings
        val isBomb = projectile.hasMetadata("bombKey")
        val radius = if (isBomb) settings.weapons.bomb.paintRadius else settings.weapons.gun.paintRadius

        val hitLoc = hitBlock.location.toCenterLocation()
        paintAround(hitLoc, projectile, game, radius, excludeBlock = null)
    }

    private fun handleAdminProjectileHitOnBlock(projectile: Snowball, hitBlock: Block) {
        if (!projectile.hasMetadata("paintTeam")) return
        val paintTeam = projectile.getMetadata("paintTeam").firstOrNull()?.asInt() ?: return

        val colorMap = mapOf(
            0 to Material.RED_CONCRETE,
            1 to Material.YELLOW_CONCRETE,
            2 to Material.GREEN_CONCRETE,
            3 to Material.BLUE_CONCRETE
        )

        val color = colorMap[paintTeam] ?: return

        val settings = SplatoonPlugin.instance.settings
        val isBomb = projectile.hasMetadata("bombKey")
        val radius = if (isBomb) settings.weapons.bomb.paintRadius else settings.weapons.gun.paintRadius
        explosivePaint(hitBlock.location.toCenterLocation(), color, radius)
    }

    private fun handleDeath(projectile: Snowball, game: Game, attacker: Player, victim: Player) {
        val attackerTeam = game.commands[attacker.uniqueId] ?: return
        val victimTeam = game.commands[victim.uniqueId] ?: return
        if (attackerTeam == victimTeam) return

        val isBomb = projectile.hasMetadata("bombKey")
        val killRadius = if (isBomb) SplatoonPlugin.instance.settings.weapons.bomb.killPaintRadius else SplatoonPlugin.instance.settings.weapons.gun.killPaintRadius

        game.kills[attacker.uniqueId] = (game.kills[attacker.uniqueId] ?: 0) + 1

        val titleObj = net.kyori.adventure.title.Title.title(
            Component.text("СПЛАТ!", NamedTextColor.RED),
            Component.text("Вы убили ${victim.name}", NamedTextColor.GRAY),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(100),
                java.time.Duration.ofMillis(900),
                java.time.Duration.ofMillis(100)
            )
        )
        attacker.showTitle(titleObj)

        victim.playSound(Sound.sound(Key.key("entity.player.hurt"), Sound.Source.MASTER, 1.0f, 0.6f))
        attacker.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.8f, 1.2f))

        val deathLoc = victim.location.clone()

        game.resetInkHp(victim.uniqueId)

        val w = Bukkit.getWorld(game.worldName) ?: return
        victim.teleport(w.spawnLocation)

        val afterRespawnMs = SplatoonPlugin.instance.settings.spawnProtection.afterRespawnSeconds * 1000L
        game.setSpawnProtection(victim.uniqueId, afterRespawnMs)

        val sp = SplatoonPlugin.instance.settings.spawnProtection
        if (sp.resistanceDurationTicks > 0) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, sp.resistanceDurationTicks, sp.resistanceAmplifier))
        }
        victim.noDamageTicks = sp.noDamageTicks

        paintAround(deathLoc, projectile, game, killRadius, excludeBlock = null)
    }

    private fun isSpawnSafe(player: Player, game: Game, radius: Double): Boolean {
        val w = Bukkit.getWorld(game.worldName) ?: return false
        if (player.world.name != w.name) return false
        if (game.isSpawnProtectionActive(player.uniqueId)) return true

        val spawn = w.spawnLocation
        val dx = player.location.x - spawn.x
        val dy = player.location.y - spawn.y
        val dz = player.location.z - spawn.z
        val distSq = dx * dx + dy * dy + dz * dz
        return distSq <= radius * radius
    }

    private fun isStandingOnTeamInk(player: Player, team: Int, game: Game): Boolean {
        val blockUnder = player.location.block.getRelative(0, -1, 0)
        val expected = game.commandColors[team]
        return expected != null && blockUnder.type == expected
    }

    private fun paintAround(center: Location, projectile: Snowball, game: Game, radius: Double, excludeBlock: Block?) {
        if (!projectile.hasMetadata("paintTeam") || !projectile.hasMetadata("baseTeam")) return
        val paintTeam = projectile.getMetadata("paintTeam").firstOrNull()?.asInt() ?: return
        val baseTeam = projectile.getMetadata("baseTeam").firstOrNull()?.asInt() ?: paintTeam
        val shooterUuid = getShooterUuid(projectile) ?: return

        val material = game.commandColors[paintTeam] ?: return
        val w = center.world ?: return

        val r = radius.coerceAtLeast(0.0)
        val minX = floor(center.x - r).toInt()
        val maxX = ceil(center.x + r).toInt()
        val minY = floor(center.y - r).toInt()
        val maxY = ceil(center.y + r).toInt()
        val minZ = floor(center.z - r).toInt()
        val maxZ = ceil(center.z + r).toInt()

        val rSq = r * r

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val dx = (x + 0.5) - center.x
                    val dy = (y + 0.5) - center.y
                    val dz = (z + 0.5) - center.z
                    if (dx * dx + dy * dy + dz * dz > rSq) continue

                    val b = w.getBlockAt(x, y, z)
                    if (excludeBlock != null && b.location == excludeBlock.location) continue
                    paintSingleBlock(b, game, shooterUuid, baseTeam, paintTeam, material)
                }
            }
        }
    }

    private fun paintSingleBlock(block: Block, game: Game, shooter: UUID, baseTeam: Int, paintTeam: Int, paintMaterial: Material) {
        if (!paintable.contains(block.type)) return
        if (block.type == paintMaterial) return

        val oldMaterial = block.type
        block.type = paintMaterial

        val oldTeam = game.commandColors.entries.firstOrNull { it.value == oldMaterial }?.key
        if (oldTeam != null) {
            game.paintedCommand[oldTeam] = (game.paintedCommand[oldTeam] ?: 0) - 1
        }
        game.paintedCommand[paintTeam] = (game.paintedCommand[paintTeam] ?: 0) + 1
        val cur = game.paintedPerson[shooter] ?: 0

        val oldWasBase = oldTeam == baseTeam
        val newIsBase = paintTeam == baseTeam

        val next = when {
            oldWasBase && !newIsBase -> cur - 1
            !oldWasBase && newIsBase -> cur + 1
            else -> cur
        }

        game.paintedPerson[shooter] = next
    }

    private fun explosivePaint(center: Location, color: Material, radius: Double) {
        val w = center.world ?: return
        val r = radius.coerceAtLeast(0.0)
        val minX = floor(center.x - r).toInt()
        val maxX = ceil(center.x + r).toInt()
        val minY = floor(center.y - r).toInt()
        val maxY = ceil(center.y + r).toInt()
        val minZ = floor(center.z - r).toInt()
        val maxZ = ceil(center.z + r).toInt()
        val rSq = r * r

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val dx = (x + 0.5) - center.x
                    val dy = (y + 0.5) - center.y
                    val dz = (z + 0.5) - center.z
                    if (dx * dx + dy * dy + dz * dz > rSq) continue

                    val b = w.getBlockAt(x, y, z)
                    if (!paintable.contains(b.type)) continue
                    b.type = color
                }
            }
        }
    }
}
