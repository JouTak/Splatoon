package ru.joutak.splatoon.util

import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import java.util.UUID

object SplatoonAttributes {

    // Важно: UUID должен быть стабильным, чтобы корректно снимать модификатор после использования.
    private val bowNoSlowId: UUID = UUID.fromString("c3d2df3d-3bdf-4a88-9f57-6c1b6e6a4d1c")

    // Лук замедляет движение во время натяжения примерно до 20%.
    // Плюс во время натяжения игрок, как правило, перестаёт быть sprinting.
    private const val bowSlowMultiplier = 0.20
    private const val sprintMultiplier = 1.30

    // В 1.21+ часть Attribute.* имеет новые имена без GENERIC_. Не привязываемся к конкретному enum-значению.
    private val movementSpeedAttr: Attribute? by lazy {
        runCatching { Attribute.valueOf("MOVEMENT_SPEED") }.getOrNull()
            ?: runCatching { Attribute.valueOf("GENERIC_MOVEMENT_SPEED") }.getOrNull()
    }

    fun applyBowNoSlow(player: Player, wasSprinting: Boolean) {
        val attrType = movementSpeedAttr ?: return
        val attr = player.getAttribute(attrType) ?: return

        // Хотим, чтобы скорость во время натяжения соответствовала скорости до натяжения.
        // Если игрок был в спринте, компенсируем и его тоже.
        val targetMultiplier = if (wasSprinting) sprintMultiplier else 1.0
        val compensationFactor = targetMultiplier / bowSlowMultiplier
        val amount = compensationFactor - 1.0

        // Обновляем модификатор даже если он уже есть (чтобы корректно учитывать wasSprinting).
        val existing = attr.modifiers.firstOrNull { it.uniqueId == bowNoSlowId }
        if (existing != null) {
            attr.removeModifier(existing)
        }

        attr.addModifier(
            AttributeModifier(
                bowNoSlowId,
                "splatoon_bow_no_slow",
                amount,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
            )
        )
    }

    fun removeBowNoSlow(player: Player) {
        val attrType = movementSpeedAttr ?: return
        val attr = player.getAttribute(attrType) ?: return
        val existing = attr.modifiers.firstOrNull { it.uniqueId == bowNoSlowId } ?: return
        attr.removeModifier(existing)
    }
}
