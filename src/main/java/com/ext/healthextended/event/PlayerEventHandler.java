package com.ext.healthextended.event;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.PlayerBodyData;
import com.ext.healthextended.data.HediffDef;
import com.ext.healthextended.logic.EffectConversionLogic;
import com.ext.healthextended.logic.HediffLogic;
import com.ext.healthextended.logic.LocationalHealthLogic;
import com.ext.healthextended.registry.ModAttachmentTypes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.EffectCures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerEventHandler {

    private final Set<UUID> pendingHeadDeaths = new HashSet<>();
    private final Map<UUID, Integer> hiddenNaturalRegenTimers = new HashMap<>();
    private final Map<UUID, RecentImpactContext> recentImpactContexts = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        // Calling getData initialises the attachment via the default factory if not yet present.
        player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        pendingHeadDeaths.remove(playerId);
        hiddenNaturalRegenTimers.remove(playerId);
        recentImpactContexts.remove(playerId);
    }

    @SubscribeEvent
    public void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance == null || effectInstance.getEffect().value().isInstantenous()) {
            return;
        }

        PlayerBodyData bodyData = player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        BodyPart recentImpactPart = resolveRecentImpactPart(player, event.getEffectSource());
        if (EffectConversionLogic.syncAddedStatusEffect(bodyData, player, effectInstance, event.getEffectSource(), recentImpactPart)) {
            player.syncData(ModAttachmentTypes.PLAYER_BODY_DATA);
        }
    }

    @SubscribeEvent
    public void onMobEffectRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (event.getCure() != EffectCures.MILK) {
            return;
        }

        PlayerBodyData bodyData = player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        if (EffectConversionLogic.removeMilkCurableHediffs(bodyData)) {
            player.syncData(ModAttachmentTypes.PLAYER_BODY_DATA);
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(BreakSpeed event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        PlayerBodyData bodyData = player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        EffectConversionLogic.applyBreakSpeedStatusOverrides(bodyData, event);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        if (pendingHeadDeaths.contains(player.getUUID())) {
            return;
        }

        PlayerBodyData bodyData = player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        BodyPart recentImpactPart = resolveRecentImpactPart(player, primarySourceEntity(event.getSource()));
        boolean handledSpecialEffectDamage = false;
        if (isHeartAttackSuffocationDamage(player, bodyData, event)) {
            handledSpecialEffectDamage = HediffLogic.applySpecialSeverity(
                    bodyData,
                    BodyPart.HEAD,
                    HediffDef.SUFFOCATION,
                    HediffLogic.getHeartAttackSuffocationPerTick(),
                    player.getRandom(),
                    "Caused by Heart Attack"
            );
        } else {
            handledSpecialEffectDamage = EffectConversionLogic.handleSpecialPlayerEffectDamage(bodyData, player, event.getSource(), event.getNewDamage(), recentImpactPart);
        }
        if (!handledSpecialEffectDamage && !isStarvationDamage(event)) {
            rememberRecentImpact(player, event.getSource(), LocationalHealthLogic.applyDamage(bodyData, player, event.getSource(), event.getNewDamage()));
        }
        HediffLogic.updateSpecialHediffs(bodyData, player.tickCount);
        player.syncData(ModAttachmentTypes.PLAYER_BODY_DATA);
        event.setNewDamage(0.0f);

        if (LocationalHealthLogic.isHeadDestroyed(bodyData) && !player.isDeadOrDying()) {
            pendingHeadDeaths.add(player.getUUID());
            return;
        }

        float projectedHealth = LocationalHealthLogic.getProjectedVanillaHealth(bodyData, player.getMaxHealth());
        if (Math.abs(player.getHealth() - projectedHealth) > 0.01f) {
            player.setHealth(projectedHealth);
        }
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        PlayerBodyData bodyData = player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        boolean healed = EffectConversionLogic.handleSpecialPlayerHealing(bodyData, player, event.getAmount());
        if (!healed) {
            healed = HediffLogic.applyHealing(bodyData, event.getAmount(), player.getRandom());
        }

        if (!healed) {
            return;
        }

        player.syncData(ModAttachmentTypes.PLAYER_BODY_DATA);
        event.setAmount(0.0f);

        float projectedHealth = LocationalHealthLogic.getProjectedVanillaHealth(bodyData, player.getMaxHealth());
        if (Math.abs(player.getHealth() - projectedHealth) > 0.01f) {
            player.setHealth(projectedHealth);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        UUID playerId = player.getUUID();
        if (pendingHeadDeaths.contains(playerId)) {
            if (!player.isDeadOrDying()) {
                player.kill();
            }
            if (player.isDeadOrDying()) {
                pendingHeadDeaths.remove(playerId);
            }
            return;
        }

        PlayerBodyData bodyData = player.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        boolean changed = EffectConversionLogic.syncVanillaStatusEffects(bodyData, player);
        changed |= HediffLogic.updateSpecialHediffs(bodyData, player.tickCount);
        changed |= EffectConversionLogic.syncConditionDrivenStatuses(bodyData);
        EffectConversionLogic.applyConditionDrivenVanillaEffects(player, bodyData);
        EffectConversionLogic.applyLocationalStatusOverrides(bodyData, player);
        changed |= HediffLogic.updateStarvation(bodyData, player.getFoodData().getFoodLevel() <= 0, player.tickCount);
        if (bodyData.getHealth(BodyPart.TORSO).getHediff(HediffDef.HEART_ATTACK) != null
                && tickHeartAttackSuffocation(player, player.tickCount)) {
            changed = true;
        }
        changed |= tickHiddenNaturalRegeneration(player, bodyData);
        if (LocationalHealthLogic.isHeadDestroyed(bodyData) && !player.isDeadOrDying()) {
            pendingHeadDeaths.add(playerId);
            if (changed) {
                player.syncData(ModAttachmentTypes.PLAYER_BODY_DATA);
            }
            return;
        }

        float projectedHealth = LocationalHealthLogic.getProjectedVanillaHealth(bodyData, player.getMaxHealth());
        if (Math.abs(player.getHealth() - projectedHealth) > 0.01f) {
            player.setHealth(projectedHealth);
        }
        if (changed) {
            player.syncData(ModAttachmentTypes.PLAYER_BODY_DATA);
        }
    }

    private boolean tickHiddenNaturalRegeneration(Player player, PlayerBodyData bodyData) {
        UUID playerId = player.getUUID();
        float projectedHealth = LocationalHealthLogic.getProjectedVanillaHealth(bodyData, player.getMaxHealth());
        if (!HediffLogic.hasHealableHediffs(bodyData) || projectedHealth < player.getMaxHealth() - 0.01f) {
            hiddenNaturalRegenTimers.remove(playerId);
            return false;
        }

        FoodData foodData = player.getFoodData();
        boolean naturalRegen = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (!naturalRegen) {
            hiddenNaturalRegenTimers.remove(playerId);
            return false;
        }

        int timer = hiddenNaturalRegenTimers.getOrDefault(playerId, 0);
        if (foodData.getSaturationLevel() > 0.0f && foodData.getFoodLevel() >= 20) {
            timer++;
            if (timer >= 10) {
                float healAmount = Math.min(foodData.getSaturationLevel(), 6.0f) / 6.0f;
                if (HediffLogic.applyHealing(bodyData, healAmount, player.getRandom())) {
                    foodData.addExhaustion(Math.min(foodData.getSaturationLevel(), 6.0f));
                    hiddenNaturalRegenTimers.put(playerId, 0);
                    return true;
                }
                timer = 0;
            }
            hiddenNaturalRegenTimers.put(playerId, timer);
            return false;
        }

        if (foodData.getFoodLevel() >= 18) {
            timer++;
            if (timer >= 80) {
                if (HediffLogic.applyHealing(bodyData, 1.0f, player.getRandom())) {
                    foodData.addExhaustion(6.0f);
                    hiddenNaturalRegenTimers.put(playerId, 0);
                    return true;
                }
                timer = 0;
            }
            hiddenNaturalRegenTimers.put(playerId, timer);
            return false;
        }

        hiddenNaturalRegenTimers.remove(playerId);
        return false;
    }

    private static boolean tickHeartAttackSuffocation(Player player, int tickCount) {
        if (tickCount % 20 != 0 || player.isEyeInFluid(FluidTags.WATER)) {
            return false;
        }

        return player.hurt(player.damageSources().drown(), 1.0f);
    }

    private static boolean isHeartAttackSuffocationDamage(Player player, PlayerBodyData bodyData, LivingDamageEvent.Pre event) {
        return bodyData.getHealth(BodyPart.TORSO).getHediff(HediffDef.HEART_ATTACK) != null
                && !player.isEyeInFluid(FluidTags.WATER)
                && event.getSource().getDirectEntity() == null
                && event.getSource().getEntity() == null
                && event.getSource().getMsgId().toLowerCase(Locale.ROOT).replace("_", "").equals("drown");
    }

    private void rememberRecentImpact(Player player, net.minecraft.world.damagesource.DamageSource source, BodyPart part) {
        recentImpactContexts.put(
                player.getUUID(),
                new RecentImpactContext(
                        player.level().getGameTime(),
                        part,
                        source.getEntity() == null ? null : source.getEntity().getUUID(),
                        source.getDirectEntity() == null ? null : source.getDirectEntity().getUUID()
                )
        );
    }

    private BodyPart resolveRecentImpactPart(Player player, Entity effectSource) {
        if (effectSource == null) {
            return null;
        }

        RecentImpactContext context = recentImpactContexts.get(player.getUUID());
        if (context == null || player.level().getGameTime() - context.gameTime() > 10L) {
            return null;
        }

        UUID sourceId = effectSource.getUUID();
        if (sourceId.equals(context.sourceEntityId()) || sourceId.equals(context.directEntityId())) {
            return context.part();
        }

        return null;
    }

    private static Entity primarySourceEntity(net.minecraft.world.damagesource.DamageSource source) {
        if (source.getDirectEntity() != null) {
            return source.getDirectEntity();
        }
        return source.getEntity();
    }

    private static boolean isStarvationDamage(LivingDamageEvent.Pre event) {
        return event.getSource().getMsgId().toLowerCase(Locale.ROOT).replace("_", "").equals("starve");
    }

    private record RecentImpactContext(long gameTime, BodyPart part, UUID sourceEntityId, UUID directEntityId) {
    }
}
