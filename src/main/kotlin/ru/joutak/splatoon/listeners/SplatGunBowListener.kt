package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
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
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.lang.reflect.Method
import java.util.UUID
import kotlin.random.Random

class SplatGunBowListener(private val plugin: Plugin) : Listener {

    private val gunKey = NamespacedKey(plugin, "splatGun")
    private val fallbackAmmoKey = NamespacedKey(plugin, "splatAmmo")
    private val adminKey = NamespacedKey(plugin, "splatoonAdmin")
    private val adminTeamKey = NamespacedKey(plugin, "adminTeam")

    private val firingTasks = mutableMapOf<UUID, BukkitTask>()
    private val nextShotAtMs = mutableMapOf<UUID, Long>()
    private val lastInputAtMs = mutableMapOf<UUID, Long>()

    // Если клиент держит ПКМ, но сервер тикает stopUsingItem(), могут быть 1-2 тика без isHandRaised.
    // Оставляем небольшой grace, чтобы удержание не обрывалось.
    private val holdGraceMs = 250L

    // Paper API: Player.startUsingItem / Player.stopUsingItem. Достаём reflection'ом, чтобы не привязываться к конкретной сборке.
    private var startUsingMethod: Method? = null
    private var stopUsingMethod: Method? = null
    private var usingMethodsResolved = false

    // Ближе к старому поведению "лопаты": заметно медленнее, чем пулемёт.
    private val fireIntervalMs = 300L

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (item.type != Material.BOW) return

        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(gunKey, PersistentDataType.BOOLEAN)) return

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return

        val game = GameManager.playerGame[player.uniqueId]
        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && pdc.has(adminKey, PersistentDataType.BOOLEAN)
        if (game == null && !isAdminUse) return

        // Если игрок начал пользоваться пушкой — снимаем спавнпротекшн (как deathmatch).
        if (game != null) {
            game.cancelSpawnProtectionByAction(player)
        }

        // Без стрел клиент может вообще не начать натяжение по воздуху.
        // Поэтому держим 1 "патрон" (ARROW с ресурс-пак моделью под снежок) в инвентаре.
        // Он не тратится, потому что ванильный выстрел стрелой отменён.
        val (baseTeam, paintTeam) = resolveTeams(player, game, pdc) ?: return
        ensureAmmoFallback(player, teamToColorName(paintTeam))

        // Если на сборке доступен startUsingItem — форсим "using item" сервером (чтобы не зависеть от нюансов клиента).
        // Для клика по блоку отменяем ванильное взаимодействие, чтобы не открывать/не нажимать блоки.
        val forced = tryStartUsingItem(player)
        if (forced && action == Action.RIGHT_CLICK_BLOCK) {
            event.isCancelled = true
        }

        // Чтобы поведение было как раньше (сразу выстрел на ПКМ), делаем первый выстрел мгновенно.
        lastInputAtMs[player.uniqueId] = System.currentTimeMillis()
        nextShotAtMs[player.uniqueId] = System.currentTimeMillis() + fireIntervalMs
        fireOnce(player, game, isAdminUse, baseTeam, paintTeam)

        startFiring(player, game, isAdminUse)
    }

    // На отпускании ПКМ у лука будет пытаться выстрелить ванильной стрелой — отключаем.
    @EventHandler(ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val item = event.bow ?: return
        if (item.type != Material.BOW) return

        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(gunKey, PersistentDataType.BOOLEAN)) return

        stopFiring(shooter.uniqueId)

        event.isCancelled = true
        event.setConsumeItem(false)
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

            // No-slow без speed-эффектов/атрибутов:
            // клиент замедляется только пока находится в состоянии using-item. Мы сбрасываем его каждый тик.
            val now = System.currentTimeMillis()
            val raised = player.isHandRaised
            if (raised) {
                lastInputAtMs[uuid] = now
                tryStopUsingItem(player)
            } else {
                val last = lastInputAtMs[uuid] ?: 0L
                if (now - last > holdGraceMs) {
                    stopFiring(uuid)
                    return@Runnable
                }
            }

            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return@Runnable

            val next = nextShotAtMs[uuid] ?: 0L
            if (now < next) return@Runnable
            nextShotAtMs[uuid] = now + fireIntervalMs

            val item = player.inventory.itemInMainHand
            val meta = item.itemMeta ?: return@Runnable
            val teams = resolveTeams(player, game, meta.persistentDataContainer) ?: return@Runnable
            fireOnce(player, game, isAdminUse, teams.first, teams.second)
        }, 0L, 1L)

        firingTasks[uuid] = task
    }

    private fun fireOnce(player: Player, game: Game?, isAdminUse: Boolean, baseTeam: Int, paintTeam: Int) {
        val colorName = teamToColorName(paintTeam)
        // "Патрон" всегда лежит в инвентаре, а имя обновляем под текущий цвет (Bacillus).
        ensureAmmoFallback(player, colorName)

        val projectileItem = createProjectileItem(colorName)
        val dir = player.eyeLocation.direction.normalize()
        val muzzle = muzzleLocation(player.eyeLocation, dir)

        player.world.playSound(
            muzzle,
            Sound.ENTITY_SNOWBALL_THROW,
            0.6f,
            (1.2f + Random.nextFloat() * 0.2f)
        )

        player.world.spawn(muzzle, Snowball::class.java).apply {
            item = projectileItem
            setGravity(!SplatoonSettings.gunDisableGravity)
            velocity = dir.clone().multiply(SplatoonSettings.gunVelocity)
            shooter = player

            setMetadata("paintKey", FixedMetadataValue(plugin, 1))
            setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
            setMetadata("baseTeam", FixedMetadataValue(plugin, baseTeam))
            setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
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
        if (item.type != Material.BOW) return false

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

        val p = plugin.server.getPlayer(uuid)
        if (p != null) {
            tryStopUsingItem(p)
        }
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

        // Ищем наш "патрон" по PDC, чтобы не трогать чужие стрелы (если вдруг они попадут игроку).
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

            // Кладём в основной инвентарь (не в хотбар), чтобы не занимать слоты, но лук работал.
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

    private fun tryStartUsingItem(player: Player): Boolean {
        resolveUsingMethodsIfNeeded(player)
        val method = startUsingMethod ?: return false
        return runCatching {
            method.invoke(player, EquipmentSlot.HAND)
            true
        }.getOrDefault(false)
    }

    private fun tryStopUsingItem(player: Player) {
        resolveUsingMethodsIfNeeded(player)
        val method = stopUsingMethod ?: return
        runCatching { method.invoke(player) }
    }

    private fun resolveUsingMethodsIfNeeded(sample: Player?) {
        if (usingMethodsResolved) return
        usingMethodsResolved = true

        val clazz = sample?.javaClass ?: Player::class.java

        startUsingMethod = clazz.methods.firstOrNull { m ->
            m.name == "startUsingItem" && m.parameterCount == 1 && m.parameterTypes[0] == EquipmentSlot::class.java
        }

        stopUsingMethod = clazz.methods.firstOrNull { m ->
            m.name == "stopUsingItem" && m.parameterCount == 0
        }
    }

    private fun muzzleLocation(eye: Location, dir: Vector): Location {
        val up = Vector(0.0, 1.0, 0.0)
        val right = up.clone().crossProduct(dir).normalize()
        return eye.clone()
            .add(dir.clone().multiply(0.35))
            .add(right.multiply(0.18))
            .add(0.0, -0.35, 0.0)
    }
}
