package ru.joutak.splatoon.listeners

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent

class ProjectileHitListener : Listener {
    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent){
        val entity = event.entity
        val block = event.hitBlock
        val blockface = event.hitBlockFace
        val blocks = mutableListOf<Block>()

        if (block == null || blockface == null) return

        if (entity.type != EntityType.SNOWBALL) return
        block.type = Material.GREEN_CONCRETE

        if (blockface == BlockFace.EAST || blockface == BlockFace.WEST){
            blocks.addAll(
                listOf(
            entity.world.getBlockAt(block.location.add(0.0, 1.0, 0.0)),
            entity.world.getBlockAt(block.location.add(0.0, 0.0, 1.0)),
            entity.world.getBlockAt(block.location.add(0.0, -1.0, 0.0)),
            entity.world.getBlockAt(block.location.add(0.0, 0.0, -1.0))))
        }
        if (blockface == BlockFace.DOWN || blockface == BlockFace.UP){
            blocks.addAll(
                listOf(
            entity.world.getBlockAt(block.location.add(1.0, 0.0, 0.0)),
            entity.world.getBlockAt(block.location.add(0.0, 0.0, 1.0)),
            entity.world.getBlockAt(block.location.add(-1.0, 0.0, 0.0)),
            entity.world.getBlockAt(block.location.add(0.0, 0.0, -1.0))))
        }
        if (blockface == BlockFace.NORTH || blockface == BlockFace.SOUTH){
            blocks.addAll(
                listOf(
            entity.world.getBlockAt(block.location.add(0.0, 1.0, 0.0)),
            entity.world.getBlockAt(block.location.add(1.0, 0.0, 0.0)),
            entity.world.getBlockAt(block.location.add(0.0, -1.0, 0.0)),
            entity.world.getBlockAt(block.location.add(-1.0, 0.0, 0.0))))
        }

        for (b in blocks){
            if (b.getRelative(blockface).type != Material.AIR) continue
            if (b.type != Material.AIR) b.type = Material.GREEN_CONCRETE
        }

    }
}