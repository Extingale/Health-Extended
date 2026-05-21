package com.ext.healthextended.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures the exact 3-D impact position of projectiles striking players,
 * keyed by the projectile's entity UUID.
 *
 * <p>The sequence on the server thread is:
 * <ol>
 *   <li>{@link ProjectileImpactEvent} fires → we store the impact position.</li>
 *   <li>The projectile's {@code onHit} logic calls {@code player.hurt(…)}.</li>
 *   <li>{@link net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre} fires
 *       → {@link PlayerEventHandler} calls {@link #consumeImpact} to retrieve
 *       the position before delegating to {@link com.ext.healthextended.logic.HitLocationResolver}.</li>
 * </ol>
 * All three steps happen synchronously on the same thread within a single tick,
 * so no concurrent-access concerns arise.</p>
 */
public final class ProjectileImpactTracker {

    /** Ticks before a stored impact is discarded even if never consumed. */
    private static final int MAX_AGE_TICKS = 3;

    private final Map<UUID, StoredImpact> pending = new HashMap<>();
    private int currentTick = 0;

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof Player)) {
            return;
        }
        UUID id = event.getProjectile().getUUID();
        pending.put(id, new StoredImpact(event.getRayTraceResult().getLocation(), currentTick));
    }

    /**
     * Consumes and returns the stored impact position for {@code directEntity},
     * or {@code null} if no unexpired entry exists.
     */
    @Nullable
    public Vec3 consumeImpact(@Nullable Entity directEntity) {
        if (directEntity == null) {
            return null;
        }
        StoredImpact stored = pending.remove(directEntity.getUUID());
        if (stored == null) {
            return null;
        }
        if (currentTick - stored.storedTick() > MAX_AGE_TICKS) {
            return null;
        }
        return stored.pos();
    }

    /**
     * Advances the internal tick counter and evicts stale entries.
     * Called by {@link PlayerEventHandler} on each server tick.
     */
    public void tick(int gameTick) {
        this.currentTick = gameTick;
        pending.entrySet().removeIf(e -> gameTick - e.getValue().storedTick() > MAX_AGE_TICKS);
    }

    private record StoredImpact(Vec3 pos, int storedTick) {}
}
