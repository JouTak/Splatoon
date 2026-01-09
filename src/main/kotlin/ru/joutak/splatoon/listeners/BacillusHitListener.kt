package ru.joutak.splatoon.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.time.Duration
import java.util.UUID

class BacillusHitListener(private val plugin: Plugin) : Listener {

    private val useCooldown: MutableMap<UUID, Long> = mutableMapOf()

    @EventHandler
    fun onSwing(event: PlayerAnimationEvent) {
        val player = event.player
        val game = GameManager.playerGame[player.uniqueId] ?: return

        val hand = findBacillus(player) ?: return

        val settings = SplatoonPlugin.instance.settings
        val now = System.currentTimeMillis()
        val last = useCooldown[player.uniqueId]
        if (last != null && now - last < settings.bacillus.cooldownMs) return

        val target = raytracePlayer(player, settings.bacillus.rangeBlocks) ?: return
        if (GameManager.playerGame[target.uniqueId] != game) return

        val attackerTeam = game.commands[player.uniqueId] ?: return
        val victimTeam = game.commands[target.uniqueId] ?: return
        if (attackerTeam == victimTeam) return

        if (isSpawnSafe(target, game)) return

        useCooldown[player.uniqueId] = now

        game.applyAmmoOverride(target.uniqueId, attackerTeam, settings.bacillus.durationSeconds * 1000L)
        showBacillusTitles(player, target, settings.bacillus.durationSeconds)

        consumeBacillus(player, hand)
    }

    private fun showBacillusTitles(attacker: Player, victim: Player, seconds: Int) {
        val t = Title.title(
            Component.text("☣ Бацилла!", NamedTextColor.LIGHT_PURPLE),
            Component.text("Вы заражены на ${seconds}с", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(150))
        )
        victim.showTitle(t)

        val t2 = Title.title(
            Component.text("☣ Bacillus", NamedTextColor.LIGHT_PURPLE),
            Component.text("Цель заражена!", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(900), Duration.ofMillis(150))
        )
        attacker.showTitle(t2)
    }

    private fun raytracePlayer(player: Player, maxDistance: Double): Player? {
        val start = player.eyeLocation
        val dir = start.direction
        val res = player.world.rayTraceEntities(start, dir, maxDistance, 0.3) { e ->
            e is Player && e.uniqueId != player.uniqueId
        } ?: return null

        val hit = res.hitEntity ?: return null
        return hit as? Player
    }

    private fun findBacillus(player: Player): BacillusHand? {
        val key = NamespacedKey(plugin, "Bacillus")
        val inv = player.inventory

        val main = inv.itemInMainHand
        if (main.type == Material.AMETHYST_SHARD && main.hasItemMeta()) {
            val pdc = main.itemMeta.persistentDataContainer
            if (pdc.has(key, PersistentDataType.BOOLEAN)) return BacillusHand.MAIN
        }

        val off = inv.itemInOffHand
        if (off.type == Material.AMETHYST_SHARD && off.hasItemMeta()) {
            val pdc = off.itemMeta.persistentDataContainer
            if (pdc.has(key, PersistentDataType.BOOLEAN)) return BacillusHand.OFF
        }

        return null
    }

    private fun consumeBacillus(player: Player, hand: BacillusHand) {
        val inv = player.inventory
        if (hand == BacillusHand.MAIN) {
            val it = inv.itemInMainHand
            if (it.amount <= 1) inv.setItemInMainHand(null) else it.amount = it.amount - 1
            return
        }

        val it = inv.itemInOffHand
        if (it.amount <= 1) inv.setItemInOffHand(null) else it.amount = it.amount - 1
    }

    private enum class BacillusHand {
        MAIN,
        OFF
    }

    private fun isSpawnSafe(player: Player, game: Game): Boolean {
        val w = org.bukkit.Bukkit.getWorld(game.worldName) ?: return false
        if (player.world.name != w.name) return false
        if (game.isSpawnProtectionActive(player.uniqueId)) return true

        val spawn = w.spawnLocation
        val dx = player.location.x - spawn.x
        val dy = player.location.y - spawn.y
        val dz = player.location.z - spawn.z
        val distSq = dx * dx + dy * dy + dz * dz

        val r = SplatoonPlugin.instance.settings.spawnProtection.radiusBlocks
        return distSq <= r * r
    }
}
