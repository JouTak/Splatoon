package ru.joutak.splatoon.listeners

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

class SplatGunProtectionListener(private val plugin: Plugin) : Listener {
    private val splatGunKey = NamespacedKey(plugin, "splatGun")
    private val splatAmmoKey = NamespacedKey(plugin, "splatAmmo")

    @EventHandler(ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (isSplatGun(event.itemDrop.itemStack)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val action = event.action

        if (
            action == InventoryAction.DROP_ALL_CURSOR ||
            action == InventoryAction.DROP_ONE_CURSOR
        ) {
            if (isSplatGun(event.cursor)) event.isCancelled = true
            return
        }

        if (
            action == InventoryAction.DROP_ALL_SLOT ||
            action == InventoryAction.DROP_ONE_SLOT
        ) {
            if (isSplatGun(event.currentItem)) event.isCancelled = true
            return
        }

        if (event.click == ClickType.DROP || event.click == ClickType.CONTROL_DROP) {
            if (isSplatGun(event.currentItem)) event.isCancelled = true
        }
    }

    private fun isSplatGun(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        if (!item.hasItemMeta()) return false
        val pdc = item.itemMeta.persistentDataContainer
        return pdc.has(splatGunKey, PersistentDataType.BOOLEAN) || pdc.has(splatAmmoKey, PersistentDataType.BOOLEAN)
    }
}
