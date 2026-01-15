package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.items.CrossbowVisual
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.random.Random

/**
 * Splatoon gun listener.
 *
 * We use CROSSBOW as a holder for the model, but we do NOT use vanilla shooting.
 * Shooting is handled by spawning custom snowballs.
 *
 * Important: the crossbow is kept visually "charged" ALL THE TIME to avoid jitter
 * from charging/un-charging animations. Firing depends only on right-clicks / holding.
 */
class SplatGunBowListener(private val plugin: Plugin) : Listener {

    private val gunKey = NamespacedKey(plugin, "splatGun")
    private val fallbackAmmoKey = NamespacedKey(plugin, "splatAmmo")
    private val adminKey = NamespacedKey(plugin, "splatoonAdmin")
    private val adminTeamKey = NamespacedKey(plugin, "adminTeam")

    private val firingTasks = mutableMapOf<UUID, BukkitTask>()
    private val nextShotAtMs = mutableMapOf<UUID, Long>()
    private val lastInputAtMs = mutableMapOf<UUID, Long>()

    // If player clicks once (without holding), we keep the task alive briefly and then stop.
    private val clickGraceMs = 220L

    // Fire rate when holding right-click (slower, as requested).
    private val holdIntervalMs = 300L

    // Fire rate for "click spam" (still limited a bit to avoid insane CPS abuse).
    private val clickCooldownMs = 170L

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (item.type != Material.CROSSBOW) return

        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(gunKey, PersistentDataType.BOOLEAN)) return

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return

        val game = GameManager.playerGame[player.uniqueId]
        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && pdc.has(adminKey, PersistentDataType.BOOLEAN)
        if (game == null && !isAdminUse) return

        suppressCrossbowClientSounds(player)

        // Prevent block interactions (chests/buttons) when shooting.
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.ALLOW)
        }

        // Spawn protection should be cancelled when the player starts using the gun.
        if (game != null) {
            game.setSpawnProtection(player, 0L)
        }

        // Keep the crossbow always visually charged (no toggling!).
        ensureAlwaysCharged(player)

        val (baseTeam, paintTeam) = resolveTeams(player, game, pdc) ?: return
        ensureAmmoFallback(player, teamToColorName(paintTeam))

        val now = System.currentTimeMillis()
        lastInputAtMs[player.uniqueId] = now

        // Click => shoot once (with a small cooldown).
        tryShoot(player, game, isAdminUse, baseTeam, paintTeam, now, clickCooldownMs)

        // Start task for holding right-click. If the player does not hold, it will stop quickly.
        startFiring(player, game, isAdminUse)
    }

    // Vanilla crossbow shoot is cancelled.
    @EventHandler(ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val item = event.bow ?: return
        if (item.type != Material.CROSSBOW) return

        val meta = item.itemMeta ?: return
        if (!meta.persistentDataContainer.has(gunKey, PersistentDataType.BOOLEAN)) return

        event.isCancelled = true
        event.setConsumeItem(false)


        suppressCrossbowClientSounds(shooter)

        // If vanilla tries to "discharge" the crossbow client-side, immediately restore the visual.
        ensureAlwaysCharged(shooter)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        stopFiring(event.player.uniqueId)
    }

    private fun startFiring(player: Player, game: Game?, isAdminUse: Boolean) {
        val uuid = player.uniqueId
        firingTasks[uuid]?.cancel()

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!shouldContinue(player)) {
                stopFiring(uuid)
                return@Runnable
            }

            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return@Runnable

            // Only auto-fire while the player is actually holding right-click.
            val now = System.currentTimeMillis()
            val raised = player.isHandRaised
            if (!raised) {
                val last = lastInputAtMs[uuid] ?: 0L
                if (now - last > clickGraceMs) {
                    stopFiring(uuid)
                }
                return@Runnable
            }

            lastInputAtMs[uuid] = now

            // While holding, use slower fire rate.
            val item = player.inventory.itemInMainHand
            val meta = item.itemMeta ?: return@Runnable
            val teams = resolveTeams(player, game, meta.persistentDataContainer) ?: return@Runnable
            tryShoot(player, game, isAdminUse, teams.first, teams.second, now, holdIntervalMs)
        }, 0L, 1L)

        firingTasks[uuid] = task
    }

    private fun tryShoot(
        player: Player,
        game: Game?,
        isAdminUse: Boolean,
        baseTeam: Int,
        paintTeam: Int,
        now: Long,
        cooldownMs: Long
    ) {
        val next = nextShotAtMs[player.uniqueId] ?: 0L
        if (now < next) return
        nextShotAtMs[player.uniqueId] = now + cooldownMs
        fireOnce(player, game, isAdminUse, baseTeam, paintTeam)
    }

    private fun fireOnce(player: Player, game: Game?, isAdminUse: Boolean, baseTeam: Int, paintTeam: Int) {
        val colorName = teamToColorName(paintTeam)
        ensureAmmoFallback(player, colorName)

        val projectileItem = createProjectileItem(colorName)
        val dir = player.eyeLocation.direction.normalize()
        val muzzle = muzzleLocation(player, dir)

        player.world.playSound(
            muzzle,
            Sound.ENTITY_SNOWBALL_THROW,
            0.45f,
            (0.95f + Random.nextFloat() * 0.10f)
        )

        // Use the consumer-based spawn to set the item BEFORE the entity is actually spawned,
        // otherwise clients may briefly see a default (white) snowball before metadata update.
        player.world.spawn(muzzle, Snowball::class.java) { sb ->
            sb.item = projectileItem
            sb.setGravity(!SplatoonSettings.gunDisableGravity)
            sb.velocity = dir.clone().multiply(SplatoonSettings.gunVelocity)
            sb.shooter = player

            sb.setMetadata("paintKey", FixedMetadataValue(plugin, 1))
            sb.setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
            sb.setMetadata("baseTeam", FixedMetadataValue(plugin, baseTeam))
            sb.setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
        }
    }

    private fun ensureAlwaysCharged(player: Player) {
        val item = player.inventory.itemInMainHand
        if (item.type != Material.CROSSBOW) return
        val meta = item.itemMeta ?: return
        if (!meta.persistentDataContainer.has(gunKey, PersistentDataType.BOOLEAN)) return

        // Do NOT spam inventory updates: only touch when it's actually not charged.
        val changed = CrossbowVisual.ensureCharged(item)
        if (changed) {
            player.inventory.setItemInMainHand(item)
        }
    }

    private fun resolveTeams(
        player: Player,
        game: Game?,
        pdc: org.bukkit.persistence.PersistentDataContainer
    ): Pair<Int, Int>? {
        val baseTeam = if (game != null) {
            game.commands[player.uniqueId] ?: return null
        } else {
            pdc.get(adminTeamKey, PersistentDataType.INTEGER) ?: 0
        }

        val paintTeam = if (game != null) {
            game.getAmmoTeam(player.uniqueId) ?: baseTeam
        } else {
            GameManager.getAdminAmmoTeam(player.uniqueId, baseTeam)
        }

        return baseTeam to paintTeam
    }

    private fun shouldContinue(player: Player): Boolean {
        if (!player.isOnline) return false
        val item = player.inventory.itemInMainHand
        if (item.type != Material.CROSSBOW) return false
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        if (!pdc.has(gunKey, PersistentDataType.BOOLEAN)) return false

        val game = GameManager.playerGame[player.uniqueId]
        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && pdc.has(adminKey, PersistentDataType.BOOLEAN)
        return game != null || isAdminUse
    }

    private fun stopFiring(uuid: UUID) {
        firingTasks.remove(uuid)?.cancel()
        nextShotAtMs.remove(uuid)
        lastInputAtMs.remove(uuid)

        // Do NOT change charged state: the gun stays visually charged always.
    }

    private fun createProjectileItem(name: String): ItemStack {
        val stack = ItemStack(Material.SNOWBALL, 1)
        stack.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name))
        return stack
    }

    private fun teamToColorName(team: Int): String {
        return when (team) {
            0 -> "Red"
            3 -> "Blue"
            2 -> "Green"
            1 -> "Yellow"
            else -> "Red"
        }
    }

    private fun ensureAmmoFallback(player: Player, colorName: String? = null) {
        val inv = player.inventory

        // Find our "ammo" marker so we don't touch random arrows the player could have.
        var ammoSlot = -1
        for (i in 0 until inv.size) {
            val it = inv.getItem(i) ?: continue
            if (it.type == Material.AIR || !it.hasItemMeta()) continue
            if (it.itemMeta.persistentDataContainer.has(fallbackAmmoKey, PersistentDataType.BOOLEAN)) {
                ammoSlot = i
                break
            }
        }

        val item = if (ammoSlot >= 0) {
            inv.getItem(ammoSlot) ?: return
        } else {
            val created = ItemStack(Material.ARROW, 1)
            val m = created.itemMeta
            m.persistentDataContainer.set(fallbackAmmoKey, PersistentDataType.BOOLEAN, true)
            created.itemMeta = m

            // Put it into main inventory (not hotbar).
            val slot = firstEmptyMainInvSlot(inv) ?: 35
            inv.setItem(slot, created)
            ammoSlot = slot
            created
        }

        if (colorName != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, Component.text(colorName))
            inv.setItem(ammoSlot, item)
        }
    }

    private fun firstEmptyMainInvSlot(inv: org.bukkit.inventory.PlayerInventory): Int? {
        for (i in 9..35) {
            val it = inv.getItem(i)
            if (it == null || it.type == Material.AIR) return i
        }
        return null
    }

    private fun suppressCrossbowClientSounds(player: Player) {
        // Crossbow sounds can be played client-side even if server cancels vanilla shooting.
        // We "nerf" it by stopping known crossbow sounds immediately and 1 tick later.
        val names = arrayOf(
            "ITEM_CROSSBOW_SHOOT",
            "ITEM_CROSSBOW_LOADING_START",
            "ITEM_CROSSBOW_LOADING_MIDDLE",
            "ITEM_CROSSBOW_LOADING_END",
            "ITEM_CROSSBOW_QUICK_CHARGE_1",
            "ITEM_CROSSBOW_QUICK_CHARGE_2",
            "ITEM_CROSSBOW_QUICK_CHARGE_3"
        )
        stopSoundsByName(player, names)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            stopSoundsByName(player, names)
        }, 1L)
    }

    private fun stopSoundsByName(player: Player, names: Array<String>) {
        for (n in names) {
            runCatching {
                val s = Sound.valueOf(n)
                player.stopSound(s)
            }
        }
    }

    private fun muzzleLocation(player: Player, dir: Vector): Location {
        val up = Vector(0.0, 1.0, 0.0)
        val right = up.clone().crossProduct(dir).normalize()
        // Spawn from the "gun" position (right hand / chest), not from the eye.
        // Using eyeLocation as base gives the correct direction, then we offset down + right.
        return player.eyeLocation.clone()
            .add(0.0, -0.60, 0.0)
            .add(dir.clone().multiply(0.65))
            .add(right.multiply(0.30))
    }
}
