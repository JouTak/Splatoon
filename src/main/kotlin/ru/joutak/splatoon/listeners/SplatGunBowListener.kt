package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class SplatGunBowListener(private val plugin: Plugin) : Listener {

    private val splatGunKey = NamespacedKey(plugin, "splatGun")
    private val adminKey = NamespacedKey(plugin, "splatoonAdmin")
    private val adminTeamKey = NamespacedKey(plugin, "adminTeam")

    private val commandColors = mapOf(
        0 to "Red",
        3 to "Blue",
        2 to "Green",
        1 to "Yellow"
    )

    @EventHandler(ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return

        val bow = event.bow ?: return
        if (!isSplatGun(bow)) return

        // Не стреляем в режиме кальмара.
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            event.isCancelled = true
            (event.projectile as? Arrow)?.remove()
            return
        }

        val game = GameManager.playerGame[player.uniqueId]
        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && bow.itemMeta
            .persistentDataContainer
            .has(adminKey, PersistentDataType.BOOLEAN)

        if (game == null && !isAdminUse) return

        event.isCancelled = true
        (event.projectile as? Arrow)?.remove()

        val baseTeam = if (game != null) {
            game.commands[player.uniqueId] ?: return
        } else {
            bow.itemMeta.persistentDataContainer.get(adminTeamKey, PersistentDataType.INTEGER) ?: 0
        }

        val paintTeam = if (game != null) {
            game.getAmmoTeam(player.uniqueId) ?: baseTeam
        } else {
            GameManager.getAdminAmmoTeam(player.uniqueId, baseTeam)
        }

        val colorName = commandColors[paintTeam] ?: return
        val projectileItem = createProjectileItem(colorName)

        val dir = player.eyeLocation.direction.normalize()
        val muzzle = getMuzzleLocation(player.eyeLocation, dir)

        player.world.spawn(muzzle, Snowball::class.java).apply {
            item = projectileItem
            setGravity(!SplatoonSettings.gunDisableGravity)
            velocity = dir.clone().multiply(SplatoonSettings.gunVelocity)
            this.shooter = player

            setMetadata("paintKey", FixedMetadataValue(plugin, 1))
            setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
            setMetadata("baseTeam", FixedMetadataValue(plugin, baseTeam))
            setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
        }
    }

    private fun isSplatGun(item: ItemStack): Boolean {
        if (item.type != Material.BOW) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(splatGunKey, PersistentDataType.BOOLEAN)
    }

    private fun createProjectileItem(name: String): ItemStack {
        val stack = ItemStack(Material.SNOWBALL, 1)
        stack.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name))
        return stack
    }

    private fun getMuzzleLocation(eye: Location, dir: Vector): Location {
        // Чуть ниже глаз + чуть вперед по направлению взгляда + немного вправо (как ствол в правой руке).
        val forward = dir.clone().multiply(0.35)
        val right = Vector(-dir.z, 0.0, dir.x).normalize().multiply(0.18)
        return eye.clone().add(forward).add(right).add(0.0, -0.35, 0.0)
    }
}
