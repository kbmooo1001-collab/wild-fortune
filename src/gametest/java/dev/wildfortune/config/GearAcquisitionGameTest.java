package dev.wildfortune.config;

import dev.wildfortune.gear.GearAcquisition;
import dev.wildfortune.gear.GearAffix;
import dev.wildfortune.gear.GearAffixes;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class GearAcquisitionGameTest {
    @GameTest
    public void chestPickupAndMerchantRewardsRollOnlyAfterAcquisition(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            guaranteedUsefulEffects();
            var player = test.makeMockServerPlayerInLevel();
            var chest = new SimpleContainer(27);
            chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            var menu = ChestMenu.threeRows(1, player.getInventory(), chest);
            player.containerMenu = menu;
            GearAcquisition.process(player);
            require(!GearAffixes.isProcessed(chest.getItem(0)), "Viewing chest rolled unowned loot");
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            GearAcquisition.process(player);
            var pickaxe = find(player, Items.IRON_PICKAXE);
            require(GearAffixes.getAll(pickaxe).equals(List.of(GearAffix.EFFICIENT)), "Chest loot missed tool affix");

            var drop = new ItemEntity(test.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.IRON_SWORD));
            drop.setPickUpDelay(0);
            require(test.getLevel().addFreshEntity(drop), "Could not spawn drop");
            drop.playerTouch(player);
            GearAcquisition.process(player);
            require(GearAffixes.getAll(find(player, Items.IRON_SWORD)).equals(List.of(GearAffix.SHARP)), "Picked-up drop missed affix");

            var villager = EntityTypes.VILLAGER.create(test.getLevel(), EntitySpawnReason.COMMAND);
            villager.setTradingPlayer(player);
            var offer = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.IRON_CHESTPLATE), 10, 1, 0);
            villager.getOffers().clear();
            villager.getOffers().add(offer);
            var trade = new MerchantMenu(2, player.getInventory(), villager);
            player.containerMenu = trade;
            trade.getSlot(0).set(new ItemStack(Items.EMERALD, 2));
            trade.updateSellItem();
            require(trade.getSlot(2).getItem().is(Items.IRON_CHESTPLATE), "Missing trade preview");
            GearAcquisition.process(player);
            require(!GearAffixes.isProcessed(trade.getSlot(2).getItem()) && !GearAffixes.isProcessed(offer.getResult()), "Offer/preview was rolled");
            trade.clicked(2, 0, ContainerInput.PICKUP, player);
            GearAcquisition.process(player);
            require(GearAffixes.getAll(trade.getCarried()).equals(List.of(GearAffix.STURDY)), "Trade cursor reward missed armor affix");
            require(offer.getUses() == 1 && trade.getSlot(0).getItem().getCount() == 1, "Affixing changed trade cost or uses");
            require(!GearAffixes.isProcessed(offer.getResult()) && !GearAffixes.isProcessed(trade.getSlot(2).getItem()), "Next trade inherited previous roll");
            villager.discard();
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest
    public void equipmentCursorExistingGearAndPlainResultsNeverReroll(GameTestHelper test) {
        var previous = WildFortuneConfig.get();
        try {
            var player = test.makeMockServerPlayerInLevel();
            var plain = new ItemStack(Items.IRON_SWORD);
            GearAffixes.applyAll(plain, List.of());
            var existing = new ItemStack(Items.DIAMOND_SWORD);
            GearAffixes.applyAll(existing, List.of(GearAffix.SHARP));
            var original = existing.get(DataComponents.CUSTOM_DATA);
            guaranteedUsefulEffects();
            player.getInventory().setItem(0, plain);
            player.getInventory().setItem(1, existing);
            player.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
            player.containerMenu.setCarried(new ItemStack(Items.IRON_SWORD));
            for (int i = 0; i < 20; i++) GearAcquisition.process(player);
            require(GearAffixes.isProcessed(plain) && GearAffixes.getAll(plain).isEmpty(), "Plain gear was rerolled");
            require(original.equals(existing.get(DataComponents.CUSTOM_DATA)), "Saved affix values were replaced");
            require(!GearAffixes.isProcessed(player.getInventory().getItem(2)), "Non-gear was processed");
            require(GearAffixes.isProcessed(player.getItemBySlot(EquipmentSlot.HEAD)), "Directly equipped armor not processed");
            require(GearAffixes.isProcessed(player.getItemBySlot(EquipmentSlot.OFFHAND)), "Direct offhand acquisition not processed");
            require(GearAffixes.isProcessed(player.containerMenu.getCarried()), "Cursor acquisition not processed");
        } finally { WildFortuneConfig.install(previous); }
        test.succeed();
    }

    @GameTest(maxTicks = 40)
    public void serverTickAutomaticallyProcessesGrantedEquipment(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.getInventory().setItem(7, new ItemStack(Items.BOW));
        test.runAfterDelay(2, () -> {
            require(GearAffixes.isProcessed(player.getInventory().getItem(7)), "Server event did not process inventory grant");
            test.succeed();
        });
    }

    private static ItemStack find(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(item)) return stack;
        }
        throw new AssertionError("Reward not acquired: " + item);
    }

    private static void guaranteedUsefulEffects() {
        var tree = com.google.gson.JsonParser.parseString(WildFortuneConfig.toJson(WildFortuneConfig.defaults())).getAsJsonObject();
        var effects = tree.getAsJsonObject("gear").getAsJsonObject("effects");
        for (GearAffix effect : GearAffix.values()) effects.getAsJsonObject(effect.id).addProperty("chance", 0);
        for (String id : List.of("sharp", "efficient", "sturdy")) effects.getAsJsonObject(id).addProperty("chance", 1);
        WildFortuneConfig.install(WildFortuneConfig.parse(tree.toString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
