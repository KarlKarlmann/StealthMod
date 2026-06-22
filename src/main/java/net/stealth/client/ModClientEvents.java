package net.stealth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.client.screen.StealthHudEditorScreen;

@Mod.EventBusSubscriber(modid = StealthMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModClientEvents {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            // Elegant placement at the top center of the pause menu
            int buttonWidth = 120;
            int buttonHeight = 20;
            int x = pauseScreen.width / 2 - buttonWidth / 2;
            int y = 10; // Anchored comfortably at the top, perfectly clear of existing buttons
            
            event.addListener(Button.builder(
                Component.literal("§eStealth HUD"),
                btn -> Minecraft.getInstance().setScreen(new StealthHudEditorScreen())
            ).bounds(x, y, buttonWidth, buttonHeight).build());
        }
    }
}