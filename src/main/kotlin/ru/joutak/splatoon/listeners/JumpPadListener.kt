package ru.joutak.splatoon.listeners

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BoundingBox
import ru.joutak.minigames.MiniGamesAPI.plugin
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class JumpPadListener : Listener {

    private var jumpPadMaterial: Material? = null
    private val removalTasks = mutableMapOf<Player, BukkitRunnable>()
    private val checkTasks = mutableMapOf<Player, BukkitRunnable>()
    private val stopCheckTasks = mutableMapOf<Player, BukkitRunnable>()

    private fun getJumpPadMaterial(): Material? {
        val currentBlockType = SplatoonSettings.jumpPadBlockType
        if (jumpPadMaterial?.name != currentBlockType) {
            jumpPadMaterial = Material.getMaterial(currentBlockType)
        }
        return jumpPadMaterial
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId

        val game = GameManager.playerGame[uuid] ?: return

        if (player.world.name != game.worldName) return


        val jumpPadBlock = getJumpPadMaterial()
//        val blockBelow = player.location.clone().subtract(0.0, 0.5, 0.0).block
//
//        val isOnJumpPad = blockBelow.type == jumpPadBlock
        val isOnJumpPad = isPlayerStandingOn(player, jumpPadBlock)

        if (isOnJumpPad) {
            cancelRemoval(player)
            giveInfiniteJumpBoost(player)
            if (checkTasks[player] == null) {
                startConstantCheck(player)
            }
        }
        else {
            if (checkTasks[player] != null && stopCheckTasks[player] == null) {
                scheduleStopCheck(player)
            }

            if (removalTasks[player] == null){
                scheduleEffectRemoval(player)
            }
        }
    }

    private fun startConstantCheck(player: Player) {
        val task = object : BukkitRunnable() {
            override fun run() {
                val game = GameManager.playerGame[player.uniqueId] ?: run {
                    cancel()
                    checkTasks.remove(player)
                    return
                }

                val jumpPadBlock = getJumpPadMaterial()
//                val blockBelow = player.location.clone().subtract(0.0, 0.5, 0.0).block
//                val isOnJumpPad = blockBelow.type == jumpPadBlock
                val isOnJumpPad = isPlayerStandingOn(player, jumpPadBlock)
                if (isOnJumpPad == null)
                    plugin.logger.info("BlockType == null!!!!")

                plugin.logger.info("Check")

                if (isOnJumpPad) {
                    cancelRemoval(player)
                    giveInfiniteJumpBoost(player)
                }

            }
        }

        checkTasks[player] = task
        task.runTaskTimer(plugin, 0L, 2L)
    }

    private fun scheduleStopCheck(player: Player) {
        val task = object : BukkitRunnable() {
            override fun run() {
                checkTasks[player]?.cancel()
                checkTasks.remove(player)
                stopCheckTasks.remove(player)
            }
        }
        stopCheckTasks[player] = task
        task.runTaskLater(plugin, 60L)
    }

    private fun scheduleEffectRemoval(player: Player) {
        val task = object: BukkitRunnable() {
            override fun run() {
                removeEffect(player, PotionEffectType.JUMP_BOOST)
                removalTasks.remove(player)

            }
        }

        removalTasks[player] = task
        task.runTaskLater(plugin, SplatoonSettings.jumpPadEffectDelay)
    }

    private fun cancelRemoval(player: Player) {
        removalTasks[player]?.cancel()
        removalTasks.remove(player)
    }

    private fun removeEffect(player: Player, potionEffectType: PotionEffectType) {
        player.removePotionEffect(potionEffectType)
    }

    private fun giveInfiniteJumpBoost(player: Player) {
        val currentEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST)

        val needsUpdate = currentEffect == null ||
                currentEffect.amplifier != SplatoonSettings.jumpPadJumpAmplifier ||
                currentEffect.duration < 1000000

        if (needsUpdate) {
            val infiniteDuration = Int.MAX_VALUE

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    infiniteDuration,
                    SplatoonSettings.jumpPadJumpAmplifier,
                    false,
                    false,
                    true)

            )
        }
    }

    private fun isPlayerStandingOn(player: Player, targetType: Material?): Boolean {
        val box: BoundingBox = player.boundingBox

        val y = box.minY - 0.1
        val offset = 0.2

        val corners = listOf(
            Pair(box.minX, box.minZ),
            Pair(box.maxX, box.minZ),
            Pair(box.minX, box.maxZ),
            Pair(box.maxX, box.maxZ)
        )
        return corners.any { (x, z) ->
            player.world.getBlockAt(x.toInt(), y.toInt(), z.toInt()).type == targetType
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        checkTasks[event.player]?.cancel()
        checkTasks.remove(event.player)
        removalTasks[event.player]?.cancel()
        removalTasks.remove(event.player)
    }
}