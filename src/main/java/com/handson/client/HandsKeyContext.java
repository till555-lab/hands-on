package com.handson.client;

import net.minecraftforge.client.settings.IKeyConflictContext;

/** Active only in Hands On mode and conflicts only with itself, so E / Q do not clash with the vanilla keys. */
public enum HandsKeyContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        return HandsState.modeOn;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this;
    }
}
