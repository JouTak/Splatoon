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
import java.util.UUID

class SlimeBlockListener : Listener {

    companion object {
        private val playersOnSlime = mutableListOf<UUID>()
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId

        val game = GameManager.playerGame[uuid] ?: return

        if (player.world.name != game.worldName) return

        val blockBelow = player.location.clone().subtract(0.0, 0.5, 0.0).block

        val isOnSlime = blockBelow.type == Material.SLIME_BLOCK

        if (isOnSlime && !playersOnSlime.contains(uuid)) {
            applyEffects(player)
            playersOnSlime.add(uuid)
        }
        else if (!isOnSlime && playersOnSlime.contains(uuid)) {
            removeEffects(player)
            playersOnSlime.remove(uuid)
        }
        else if (isOnSlime && playersOnSlime.contains(uuid)) {
            ensureJumpBoostActive(player)
        }
    }

    private fun applyEffects(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, SplatoonSettings.slimeBlockEffectDuration,
            SplatoonSettings.slimeBlockJumpAmplifier, false, false, true))
    }

    private fun removeEffects(player: Player) {
        player.removePotionEffect(PotionEffectType.JUMP_BOOST)
    }

    private fun ensureJumpBoostActive(player: Player) {
        var needsUpdate = false

        val jumpEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST)
        if (jumpEffect == null || jumpEffect.duration < 40) {
            needsUpdate = true
        }

       if (jumpEffect?.amplifier != SplatoonSettings.slimeBlockJumpAmplifier) {
           needsUpdate = true
       }

        if (needsUpdate) {
            removeEffects(player)
            applyEffects(player)
        }
    }
}