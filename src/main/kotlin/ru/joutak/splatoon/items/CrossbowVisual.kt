package ru.joutak.splatoon.items

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CrossbowMeta

/**
 * Keeps crossbow visually charged without depending on a specific Paper/Bukkit API.
 *
 * We use this for Splatoon gun: the model should ALWAYS look "charged" to avoid jitter
 * from charging/un-charging animations when shooting by right-click / holding.
 */
object CrossbowVisual {

    /**
     * Ensures [item] is visually charged by adding a dummy projectile.
     * Returns true if the item meta was changed.
     */
    fun ensureCharged(item: ItemStack): Boolean {
        if (item.type != Material.CROSSBOW) return false
        val meta = item.itemMeta as? CrossbowMeta ?: return false
        if (meta.chargedProjectiles.isNotEmpty()) return false

        clearCharged(meta)

        // Prefer setChargedProjectiles(List) when present.
        val setMethod = meta.javaClass.methods.firstOrNull { it.name == "setChargedProjectiles" && it.parameterCount == 1 }
        if (setMethod != null) {
            runCatching { setMethod.invoke(meta, listOf(ItemStack(Material.ARROW, 1))) }
        } else {
            // Fallback: addChargedProjectile(ItemStack)
            val addMethod = meta.javaClass.methods.firstOrNull { it.name == "addChargedProjectile" && it.parameterCount == 1 }
            if (addMethod != null) {
                runCatching { addMethod.invoke(meta, ItemStack(Material.ARROW, 1)) }
            }
        }

        item.itemMeta = meta
        return true
    }

    private fun clearCharged(meta: CrossbowMeta) {
        // 1) clearChargedProjectiles() if exists
        meta.javaClass.methods.firstOrNull { it.name == "clearChargedProjectiles" && it.parameterCount == 0 }?.let { m ->
            runCatching { m.invoke(meta) }
            return
        }

        // 2) setChargedProjectiles(List) with empty list
        meta.javaClass.methods.firstOrNull { it.name == "setChargedProjectiles" && it.parameterCount == 1 }?.let { m ->
            runCatching { m.invoke(meta, emptyList<ItemStack>()) }
            return
        }

        // 3) last resort: clear a mutable list instance
        runCatching {
            val list = meta.chargedProjectiles
            if (list is MutableList<*>) {
                @Suppress("UNCHECKED_CAST")
                (list as MutableList<ItemStack>).clear()
            }
        }
    }
}
