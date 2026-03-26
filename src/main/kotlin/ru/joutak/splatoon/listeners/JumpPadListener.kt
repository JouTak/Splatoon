package ru.joutak.splatoon.listeners

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class JumpPadListener : Listener {

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId

        val game = GameManager.playerGame[uuid] ?: return

        if (player.world.name != game.worldName) return

        val blockBelow = player.location.clone().subtract(0.0, 0.5, 0.0).block

        val isOnJumpPad = blockBelow.type == Material.LIME_CONCRETE_POWDER

        if (isOnJumpPad) {
            ensureJumpBoostActive(player)
        }
        else {
            removeEffect(player, PotionEffectType.JUMP_BOOST)
        }

    }

    private fun applyEffects(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, SplatoonSettings.jumpPadEffectDuration,
            SplatoonSettings.jumpPadJumpAmplifier, false, false, true))
    }

    private fun removeEffect(player: Player, potionEffectType: PotionEffectType) {
        player.removePotionEffect(potionEffectType)
    }

    private fun ensureJumpBoostActive(player: Player) {
        var needsUpdate = false

        val jumpEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST)
        if (jumpEffect == null || jumpEffect.duration < 40 || jumpEffect.amplifier != SplatoonSettings.jumpPadJumpAmplifier) {
            needsUpdate = true
        }

        if (needsUpdate) {
            removeEffect(player, PotionEffectType.JUMP_BOOST)
            applyEffects(player)
        }
    }
}