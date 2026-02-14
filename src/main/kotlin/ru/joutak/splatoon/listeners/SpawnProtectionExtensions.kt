package ru.joutak.splatoon.listeners

import org.bukkit.entity.Player
import ru.joutak.splatoon.scripts.Game

/**
 * Снимает спавн-протекшн по "игровому действию" (например, начало стрельбы).
 * Используем уже существующую логику Game.setSpawnProtection(...): duration=0 полностью очищает защиту.
 */
fun Game.cancelSpawnProtectionByAction(player: Player) {
    setSpawnProtection(player, 0L)
}
