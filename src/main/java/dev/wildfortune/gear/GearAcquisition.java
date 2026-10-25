package dev.wildfortune.gear;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/** Covers every acquisition path without touching merchant offers or container previews. */
public final class GearAcquisition {
    private GearAcquisition() {}

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) process(player);
        });
    }

    public static void process(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            GearAffixes.roll(inventory.getItem(index), player.getRandom());
        }
        // Equipment and cursor stacks may bypass the main inventory entirely.
        for (EquipmentSlot slot : EquipmentSlot.values()) GearAffixes.roll(player.getItemBySlot(slot), player.getRandom());
        GearAffixes.roll(player.containerMenu.getCarried(), player.getRandom());
    }
}
