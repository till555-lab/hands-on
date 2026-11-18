package com.handson.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class HandKeys {
    private static final String CATEGORY = "key.categories.handson";

    public static KeyBinding LEFT;
    public static KeyBinding RIGHT;
    public static KeyBinding LOCK;
    public static KeyBinding TOGGLE;

    public static void register() {
        LEFT = new KeyBinding("key.handson.left_hand", KeyConflictContext.IN_GAME,
                InputMappings.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CATEGORY);
        RIGHT = new KeyBinding("key.handson.right_hand", KeyConflictContext.IN_GAME,
                InputMappings.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
        LOCK = new KeyBinding("key.handson.lock", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);
        TOGGLE = new KeyBinding("key.handson.toggle", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
        ClientRegistry.registerKeyBinding(LEFT);
        ClientRegistry.registerKeyBinding(RIGHT);
        ClientRegistry.registerKeyBinding(LOCK);
        ClientRegistry.registerKeyBinding(TOGGLE);
    }
}
