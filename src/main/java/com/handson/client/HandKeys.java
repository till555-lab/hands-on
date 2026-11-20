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
    public static KeyBinding INVENTORY;
    public static KeyBinding INVENTORY_ALT;
    public static KeyBinding USE_RIGHT;
    public static KeyBinding USE_LEFT;
    public static KeyBinding RELEASE;

    public static void register() {
        LEFT = new KeyBinding("key.handson.left_hand", KeyConflictContext.IN_GAME,
                InputMappings.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CATEGORY);
        RIGHT = new KeyBinding("key.handson.right_hand", KeyConflictContext.IN_GAME,
                InputMappings.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
        LOCK = new KeyBinding("key.handson.lock", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);
        TOGGLE = new KeyBinding("key.handson.toggle", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
        INVENTORY = new KeyBinding("key.handson.inventory", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
        INVENTORY_ALT = new KeyBinding("key.handson.inventory_alt", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
        USE_RIGHT = new KeyBinding("key.handson.use_right", HandsKeyContext.INSTANCE,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);
        USE_LEFT = new KeyBinding("key.handson.use_left", HandsKeyContext.INSTANCE,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);
        RELEASE = new KeyBinding("key.handson.release", HandsKeyContext.INSTANCE,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
        ClientRegistry.registerKeyBinding(LEFT);
        ClientRegistry.registerKeyBinding(RIGHT);
        ClientRegistry.registerKeyBinding(LOCK);
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(INVENTORY);
        ClientRegistry.registerKeyBinding(INVENTORY_ALT);
        ClientRegistry.registerKeyBinding(USE_RIGHT);
        ClientRegistry.registerKeyBinding(USE_LEFT);
        ClientRegistry.registerKeyBinding(RELEASE);
    }
}
