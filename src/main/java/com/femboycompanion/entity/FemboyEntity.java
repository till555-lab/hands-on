package com.femboycompanion.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;

import com.femboycompanion.FemboyCompanion;
import com.femboycompanion.registry.ModItems;

import net.minecraft.block.Blocks;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Food;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.network.NetworkHooks;

public class FemboyEntity extends CreatureEntity {
    public static final int VARIANT_WHITE = 0;
    public static final int VARIANT_BLACK = 1;
    public static final int COAT_COUNT = 8;

    private static final DataParameter<Integer> VARIANT =
            EntityDataManager.defineId(FemboyEntity.class, DataSerializers.INT);
    private static final DataParameter<Integer> COAT =
            EntityDataManager.defineId(FemboyEntity.class, DataSerializers.INT);
    private static final DataParameter<Integer> ANGER =
            EntityDataManager.defineId(FemboyEntity.class, DataSerializers.INT);

    private int giftLimit;
    private int giftsGiven;
    private int refusals;
    private int angerTicks;
    private boolean ignoring;
    private int ignoreClicks;
    private int calmPoints;
    private long limitResetTime = -1L;
    private int lastInteractTick = -100;
    private final Map<UUID, Long> distrusted = new HashMap<>();
    private static final int POCKET_MAX = 8;

    private final List<ItemStack> pocket = new ArrayList<>();
    private boolean defender;
    private int lastHurtTick = -1000;
    private int attackPause;
    private UUID angryAt;

    public FemboyEntity(EntityType<? extends FemboyEntity> type, World world) {
        super(type, world);
        this.giftLimit = 2 + this.random.nextInt(9);
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return MobEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    public static boolean checkSpawnRules(EntityType<FemboyEntity> type, IServerWorld world,
            SpawnReason reason, BlockPos pos, Random random) {
        return world.getRawBrightness(pos, 0) > 8
                && world.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<MonsterEntity>(this, MonsterEntity.class, 10.0F, 1.0D, 1.4D) {
            @Override
            public boolean canUse() {
                return !FemboyEntity.this.defender && FemboyEntity.this.angerTicks <= 0 && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new HealFriendGoal());
        this.goalSelector.addGoal(4, new PickupItemsGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomWalkingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(7, new LookRandomlyGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<MonsterEntity>(this, MonsterEntity.class, 10,
                true, false, e -> !(e instanceof EndermanEntity) && !(e instanceof CreeperEntity)) {
            @Override
            public boolean canUse() {
                return FemboyEntity.this.defender && FemboyEntity.this.angerTicks <= 0 && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<MobEntity>(this, MobEntity.class, 200,
                true, false, e -> e instanceof ChickenEntity || e instanceof RabbitEntity) {
            @Override
            public boolean canUse() {
                return FemboyEntity.this.defender && FemboyEntity.this.angerTicks <= 0
                        && FemboyEntity.this.pocket.size() < POCKET_MAX
                        && FemboyEntity.this.getHealth() >= FemboyEntity.this.getMaxHealth() * 0.8F
                        && super.canUse();
            }
        });
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, VARIANT_WHITE);
        this.entityData.define(COAT, 0);
        this.entityData.define(ANGER, 0);
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    public int getCoat() {
        return this.entityData.get(COAT);
    }

    public void setCoat(int coat) {
        this.entityData.set(COAT, coat);
    }

    public int getAnger() {
        return this.entityData.get(ANGER);
    }

    private void syncAnger() {
        this.entityData.set(ANGER, this.isUpset() ? Math.max(1, 100 - this.calmPoints) : 0);
    }

    public ITextComponent getTypeName() {
        ITextComponent base = new TranslationTextComponent(this.getVariant() == VARIANT_BLACK
                ? "entity.femboycompanion.femboy_black"
                : "entity.femboycompanion.femboy_white");
        int anger = this.getAnger();
        if (anger > 0) {
            return new TranslationTextComponent("entity.femboycompanion.angry_suffix", base, anger);
        }
        return base;
    }

    public void distrust(UUID id, long until) {
        this.distrusted.put(id, until);
    }

    private boolean isDistrusting(UUID id, long now) {
        Long until = this.distrusted.get(id);
        if (until == null) {
            return false;
        }
        if (now >= until) {
            this.distrusted.remove(id);
            return false;
        }
        return true;
    }

    private void fire(PlayerEntity player, String event) {
        if (player instanceof ServerPlayerEntity) {
            FemboyCompanion.TRIGGER.fire((ServerPlayerEntity) player, event);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant());
        tag.putInt("Coat", this.getCoat());
        tag.putInt("GiftLimit", this.giftLimit);
        tag.putInt("GiftsGiven", this.giftsGiven);
        tag.putInt("Refusals", this.refusals);
        tag.putInt("AngerTicks", this.angerTicks);
        tag.putBoolean("Ignoring", this.ignoring);
        tag.putInt("IgnoreClicks", this.ignoreClicks);
        tag.putInt("CalmPoints", this.calmPoints);
        tag.putLong("LimitReset", this.limitResetTime);
        ListNBT list = new ListNBT();
        for (Map.Entry<UUID, Long> entry : this.distrusted.entrySet()) {
            CompoundNBT c = new CompoundNBT();
            c.putUUID("Id", entry.getKey());
            c.putLong("Until", entry.getValue());
            list.add(c);
        }
        tag.put("Distrusted", list);
        tag.putBoolean("Defender", this.defender);
        ListNBT pocketList = new ListNBT();
        for (ItemStack stack : this.pocket) {
            pocketList.add(stack.save(new CompoundNBT()));
        }
        tag.put("Pocket", pocketList);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(tag.getInt("Variant"));
        this.setCoat(tag.getInt("Coat"));
        if (tag.contains("GiftLimit")) {
            this.giftLimit = tag.getInt("GiftLimit");
        }
        this.giftsGiven = tag.getInt("GiftsGiven");
        this.refusals = tag.getInt("Refusals");
        this.angerTicks = tag.getInt("AngerTicks");
        this.ignoring = tag.getBoolean("Ignoring");
        this.ignoreClicks = tag.getInt("IgnoreClicks");
        this.calmPoints = tag.getInt("CalmPoints");
        if (tag.contains("LimitReset")) {
            this.limitResetTime = tag.getLong("LimitReset");
        }
        this.distrusted.clear();
        ListNBT list = tag.getList("Distrusted", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT c = list.getCompound(i);
            this.distrusted.put(c.getUUID("Id"), c.getLong("Until"));
        }
        this.defender = tag.getBoolean("Defender");
        this.pocket.clear();
        ListNBT pocketList = tag.getList("Pocket", 10);
        for (int i = 0; i < pocketList.size(); i++) {
            ItemStack stack = ItemStack.of(pocketList.getCompound(i));
            if (!stack.isEmpty()) {
                this.pocket.add(stack);
            }
        }
        this.syncAnger();
    }

    private static class GroupData implements ILivingEntityData {
        final int variant;

        GroupData(int variant) {
            this.variant = variant;
        }
    }

    private int rollVariant(IServerWorld world) {
        Biome biome = world.getBiome(this.blockPosition());
        Biome.Category category = biome.getBiomeCategory();
        ResourceLocation name = biome.getRegistryName();
        int whiteChance = 55;
        if (category == Biome.Category.ICY || category == Biome.Category.TAIGA) {
            whiteChance = 80;
        } else if (category == Biome.Category.SWAMP || category == Biome.Category.DESERT
                || category == Biome.Category.MESA
                || (name != null && name.getPath().equals("dark_forest"))) {
            whiteChance = 20;
        }
        return this.random.nextInt(100) < whiteChance ? VARIANT_WHITE : VARIANT_BLACK;
    }

    private int rollCoat() {
        return this.random.nextInt(3) == 0 ? 0 : 1 + this.random.nextInt(COAT_COUNT - 1);
    }

    @Override
    public ILivingEntityData finalizeSpawn(IServerWorld world, DifficultyInstance difficulty,
            SpawnReason reason, @Nullable ILivingEntityData data, @Nullable CompoundNBT tag) {
        int variant;
        if (data instanceof GroupData) {
            variant = ((GroupData) data).variant;
        } else {
            variant = this.rollVariant(world);
            data = new GroupData(variant);
        }
        this.setVariant(variant);
        this.setCoat(this.rollCoat());
        this.defender = this.random.nextInt(100) < (variant == VARIANT_BLACK ? 25 : 60);
        if (this.defender) {
            this.setItemSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        }
        this.rollPocket();
        return super.finalizeSpawn(world, difficulty, reason, data, tag);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level.isClientSide) {
            return;
        }
        if (this.angerTicks > 0) {
            this.angerTicks--;
            if (this.angerTicks == 0) {
                this.calmDown(false);
            }
        }
        if (this.attackPause > 0) {
            this.attackPause--;
            this.setTarget(null);
            if (this.attackPause == 0 && this.angerTicks > 0 && this.angryAt != null
                    && this.level.getDifficulty() != Difficulty.PEACEFUL) {
                PlayerEntity victim = this.level.getPlayerByUUID(this.angryAt);
                if (victim != null && !victim.isCreative() && !victim.isSpectator()) {
                    this.setTarget(victim);
                }
            }
        }
        if (this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth() * 0.6F
                && this.getTarget() == null && this.tickCount - this.lastHurtTick > 100) {
            PlayerEntity nearest = this.level.getNearestPlayer(this, 16.0D);
            if (this.angerTicks <= 0 || nearest == null) {
                ItemStack food = this.takeFood();
                if (!food.isEmpty()) {
                    this.consume(food);
                }
            }
        }
        if (this.tickCount % 20 == 0) {
            PlayerEntity near = this.level.getNearestPlayer(this, 10.0D);
            if (near != null) {
                this.fire(near, this.getVariant() == VARIANT_BLACK ? "meet_black" : "meet_white");
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result) {
            this.lastHurtTick = this.tickCount;
        }
        if (result && !this.level.isClientSide && this.isAlive() && source.getEntity() instanceof PlayerEntity) {
            PlayerEntity attacker = (PlayerEntity) source.getEntity();
            this.attackPause = 0;
            if (this.angerTicks <= 0) {
                this.startAnger(attacker);
            } else if (this.level.getDifficulty() != Difficulty.PEACEFUL && !attacker.isCreative()
                    && !attacker.isSpectator()) {
                this.setTarget(attacker);
            }
        }
        return result;
    }

    private boolean isUpset() {
        return this.angerTicks > 0 || this.ignoring;
    }

    private void startAnger(PlayerEntity player) {
        this.angryAt = player.getUUID();
        this.attackPause = 0;
        this.angerTicks = 6000;
        this.ignoring = false;
        this.ignoreClicks = 0;
        this.calmPoints = 0;
        this.setItemSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        this.syncAnger();
        this.say(player, "angry_start");
        this.fire(player, "sword");
        if (this.level.getDifficulty() != Difficulty.PEACEFUL && !player.isCreative() && !player.isSpectator()) {
            this.setTarget(player);
        }
    }

    private void calmDown(boolean full) {
        this.angerTicks = 0;
        this.ignoreClicks = 0;
        this.setTarget(null);
        this.setItemSlot(EquipmentSlotType.MAINHAND,
                this.defender ? new ItemStack(Items.WOODEN_SWORD) : ItemStack.EMPTY);
        if (full) {
            this.calmPoints = 0;
            this.ignoring = false;
            this.refusals = 0;
        } else {
            this.calmPoints = 50;
            this.ignoring = true;
        }
        this.syncAnger();
    }

    private static int calmPointsFor(ItemStack stack) {
        Item item = stack.getItem();
        if (ItemTags.FLOWERS.contains(item)) {
            return 10;
        }
        if (item == Items.WHEAT || item == Items.COOKIE) {
            return 5;
        }
        if (item == Items.CARROT) {
            return 6;
        }
        if (item == Items.APPLE) {
            return 8;
        }
        if (item == Items.BREAD) {
            return 15;
        }
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.COOKED_CHICKEN
                || item == Items.COOKED_MUTTON || item == Items.COOKED_RABBIT || item == Items.COOKED_COD
                || item == Items.COOKED_SALMON) {
            return 20;
        }
        if (item == Items.GOLDEN_APPLE) {
            return 50;
        }
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return 100;
        }
        return 0;
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        boolean peek = held.isEmpty() && player.isShiftKeyDown();
        int points = held.isEmpty() ? 0 : calmPointsFor(held);
        if (!held.isEmpty() && points == 0) {
            return super.mobInteract(player, hand);
        }
        if (this.level.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        if (this.tickCount - this.lastInteractTick < 10) {
            return ActionResultType.CONSUME;
        }
        this.lastInteractTick = this.tickCount;
        this.getLookControl().setLookAt(player, 30.0F, 30.0F);
        long now = this.level.getGameTime();
        UUID id = player.getUUID();

        if (peek) {
            long wait = 0L;
            if (this.isDistrusting(id, now)) {
                wait = this.distrusted.get(id) - now;
            } else if (this.giftsGiven >= this.giftLimit && this.limitResetTime > now) {
                wait = this.limitResetTime - now;
            }
            if (wait <= 0L) {
                player.displayClientMessage(
                        new TranslationTextComponent("message.femboycompanion.wait_ready"), true);
            } else {
                long seconds = wait / 20L;
                String text = (seconds / 60L) + ":" + String.format("%02d", seconds % 60L);
                player.displayClientMessage(
                        new TranslationTextComponent("message.femboycompanion.wait", text), true);
            }
            return ActionResultType.SUCCESS;
        }

        if (points > 0) {
            if (this.isUpset()) {
                if (!player.abilities.instabuild) {
                    held.shrink(1);
                }
                this.calmPoints += points;
                this.attackPause = 100;
                this.setTarget(null);
                if (this.calmPoints >= 100) {
                    this.calmDown(true);
                    this.say(player, "calm");
                    this.fire(player, "placated");
                } else {
                    this.syncAnger();
                }
                return ActionResultType.SUCCESS;
            }
            if (this.isDistrusting(id, now)) {
                long until = this.distrusted.get(id);
                long minUntil = now + 2400L;
                if (until <= minUntil) {
                    this.say(player, "pockets_full");
                    return ActionResultType.SUCCESS;
                }
                if (!player.abilities.instabuild) {
                    held.shrink(1);
                }
                this.distrusted.put(id, Math.max(minUntil, until - points * 40L));
                this.say(player, "gift_taken");
                return ActionResultType.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }

        if (now >= this.limitResetTime) {
            this.giftLimit = 2 + this.random.nextInt(9);
            this.giftsGiven = 0;
            this.refusals = 0;
            this.limitResetTime = now + 24000L;
        }
        if (this.isDistrusting(id, now)) {
            this.say(player, "distrust");
            return ActionResultType.CONSUME;
        }
        if (this.angerTicks > 0) {
            this.say(player, "angry");
            return ActionResultType.CONSUME;
        }
        if (this.ignoring) {
            this.ignoreClicks++;
            if (this.ignoreClicks >= 3) {
                this.startAnger(player);
            } else {
                this.say(player, "ignore");
            }
            return ActionResultType.SUCCESS;
        }
        if (this.giftsGiven < this.giftLimit) {
            this.giftsGiven++;
            this.giveGift(player);
            this.say(player, "gift");
            this.fire(player, "gift");
        } else {
            this.refusals++;
            if (this.refusals <= 3) {
                this.say(player, "refuse" + this.refusals);
            } else {
                this.startAnger(player);
            }
        }
        return ActionResultType.SUCCESS;
    }

    private void say(PlayerEntity player, String key) {
        player.displayClientMessage(new TranslationTextComponent("message.femboycompanion." + key), false);
    }

    private void giveGift(PlayerEntity player) {
        ItemStack stack = rollGift(this.random);
        ItemEntity item = new ItemEntity(this.level, this.getX(), this.getY() + 1.0D, this.getZ(), stack);
        Vector3d dir = player.position().subtract(this.position());
        Vector3d flat = new Vector3d(dir.x, 0.0D, dir.z).normalize().scale(0.25D);
        item.setDeltaMovement(flat.x, 0.2D, flat.z);
        item.setPickUpDelay(20);
        item.getPersistentData().putBoolean("FemboyGift", true);
        this.level.addFreshEntity(item);
    }

    private static ItemStack rollGift(Random random) {
        int r = random.nextInt(1000);
        if (r < 800) {
            Item[] common = {Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
                    Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP, Items.OXEYE_DAISY,
                    Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS,
                    Items.MELON_SEEDS, Items.BEETROOT_SEEDS, Items.BONE, Items.COD, Items.SALMON, Items.STICK,
                    Items.APPLE, Items.CARROT, Items.POTATO};
            return pick(random, common, 3);
        }
        if (r < 950) {
            Item[] uncommon = {Items.BREAD, Items.COOKIE, Items.COOKED_COD, Items.COOKED_SALMON, Items.COAL,
                    Items.STRING, Items.SUGAR};
            return pick(random, uncommon, 2);
        }
        if (r < 990) {
            Item[] rare = {Items.IRON_INGOT, Items.GOLD_NUGGET, Items.LAPIS_LAZULI};
            return pick(random, rare, 2);
        }
        if (r < 999) {
            Item[] epic = {Items.GOLD_INGOT, Items.GOLDEN_CARROT, Items.EMERALD};
            return pick(random, epic, 1);
        }
        return new ItemStack(random.nextBoolean() ? Items.DIAMOND : Items.GOLDEN_APPLE);
    }

    private static ItemStack pick(Random random, Item[] items, int maxCount) {
        return new ItemStack(items[random.nextInt(items.length)], 1 + random.nextInt(maxCount));
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        int v = this.getVariant();
        double boost = v == VARIANT_BLACK ? 1.25D : 1.0D;
        if (this.random.nextDouble() < 0.20D * boost) {
            this.spawnAtLocation(new ItemStack(ModItems.ears(v)));
        }
        if (this.random.nextDouble() < 0.15D * boost) {
            this.spawnAtLocation(new ItemStack(ModItems.tail(v)));
        }
        if (this.random.nextDouble() < 0.20D * boost) {
            this.spawnAtLocation(new ItemStack(ModItems.paws(v)));
        }
        for (ItemStack stack : this.pocket) {
            this.spawnAtLocation(stack);
        }
        this.pocket.clear();
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private void rollPocket() {
        this.pocket.clear();
        int r = this.random.nextInt(100);
        int count = r < 30 ? 0 : r < 65 ? 1 : r < 82 ? 2 : r < 92 ? 3 : r < 98 ? 4 : 5;
        Item[] foods = {Items.BREAD, Items.COOKED_CHICKEN, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
                Items.CARROT, Items.APPLE, Items.BAKED_POTATO, Items.COOKED_COD};
        for (int i = 0; i < count; i++) {
            this.pocket.add(new ItemStack(foods[this.random.nextInt(foods.length)]));
        }
    }

    private ItemStack takeFood() {
        for (int i = 0; i < this.pocket.size(); i++) {
            ItemStack stack = this.pocket.get(i);
            if (stack.getItem().isEdible()) {
                this.pocket.remove(i);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean hasFood() {
        for (ItemStack stack : this.pocket) {
            if (stack.getItem().isEdible()) {
                return true;
            }
        }
        return false;
    }

    private void consume(ItemStack food) {
        Food properties = food.getItem().getFoodProperties();
        float amount = properties != null ? properties.getNutrition() : 2.0F;
        this.heal(amount);
        this.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
    }

    private static boolean wantsItem(ItemEntity entity) {
        if (!entity.isAlive() || entity.getPersistentData().getBoolean("FemboyGift")) {
            return false;
        }
        Item item = entity.getItem().getItem();
        if (item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE || item == Items.PUFFERFISH
                || item == Items.POISONOUS_POTATO) {
            return false;
        }
        return item.isEdible() || item == Items.FEATHER || item == Items.RABBIT_HIDE
                || item == Items.RABBIT_FOOT;
    }

    private class PickupItemsGoal extends Goal {
        private ItemEntity target;

        PickupItemsGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            FemboyEntity me = FemboyEntity.this;
            if (me.pocket.size() >= POCKET_MAX || me.getTarget() != null || me.angerTicks > 0
                    || me.tickCount % 10 != 0) {
                return false;
            }
            List<ItemEntity> items = me.level.getEntitiesOfClass(ItemEntity.class,
                    me.getBoundingBox().inflate(8.0D, 2.0D, 8.0D), FemboyEntity::wantsItem);
            if (items.isEmpty()) {
                return false;
            }
            this.target = items.get(0);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            FemboyEntity me = FemboyEntity.this;
            return this.target != null && this.target.isAlive() && me.pocket.size() < POCKET_MAX
                    && me.getTarget() == null;
        }

        @Override
        public void tick() {
            FemboyEntity me = FemboyEntity.this;
            me.getNavigation().moveTo(this.target, 1.0D);
            if (me.distanceToSqr(this.target) < 2.5D) {
                ItemStack stack = this.target.getItem();
                int take = Math.min(POCKET_MAX - me.pocket.size(), stack.getCount());
                for (int i = 0; i < take; i++) {
                    me.pocket.add(new ItemStack(stack.getItem()));
                }
                stack.shrink(take);
                if (stack.isEmpty()) {
                    this.target.remove();
                } else {
                    this.target.setItem(stack);
                }
                this.target = null;
            }
        }
    }

    private class HealFriendGoal extends Goal {
        private FemboyEntity friend;

        HealFriendGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            FemboyEntity me = FemboyEntity.this;
            if (!me.hasFood() || me.getTarget() != null || me.angerTicks > 0 || me.tickCount % 20 != 0) {
                return false;
            }
            List<FemboyEntity> hurt = me.level.getEntitiesOfClass(FemboyEntity.class,
                    me.getBoundingBox().inflate(10.0D),
                    f -> f != me && f.isAlive() && f.getTarget() == null
                            && f.getHealth() < f.getMaxHealth() * 0.5F);
            if (hurt.isEmpty()) {
                return false;
            }
            this.friend = hurt.get(0);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            FemboyEntity me = FemboyEntity.this;
            return this.friend != null && this.friend.isAlive() && me.hasFood() && me.getTarget() == null
                    && this.friend.getHealth() < this.friend.getMaxHealth();
        }

        @Override
        public void tick() {
            FemboyEntity me = FemboyEntity.this;
            me.getLookControl().setLookAt(this.friend, 30.0F, 30.0F);
            me.getNavigation().moveTo(this.friend, 1.0D);
            if (me.distanceToSqr(this.friend) < 4.0D) {
                ItemStack food = me.takeFood();
                if (!food.isEmpty()) {
                    this.friend.consume(food);
                }
                this.friend = null;
            }
        }
    }
}
