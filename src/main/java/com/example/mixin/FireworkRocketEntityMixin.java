package com.example.mixin;

import com.example.PvoConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.particles.ParticleTypes;
import java.util.UUID;

import static com.example.PvoConfig.DRAG_AFTER_THRUST;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin extends Projectile {

    protected FireworkRocketEntityMixin(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }
    @Shadow
    public abstract boolean isShotAtAngle(); // Возвращает true, если ракета была выстрелена из арбалета (Mojang Mappings)

    @Shadow
    private LivingEntity attachedToEntity;

    @Shadow
    private int lifetime; // Затеняем поле ванильной длительности полета

    @Shadow
    public abstract ItemStack getItem();

    @Unique
    private static final Logger pvo$LOGGER = LoggerFactory.getLogger("pvo");

    @Unique
    private boolean pvo$active;

    @Unique
    private boolean pvo$initialized;

    @Unique
    private boolean pvo$isCandidate;

    @Unique
    private boolean pvo$exploded;

    @Unique
    private double pvo$initialSpeed;

    @Unique
    private int pvo$age;

    @Unique
    private int pvo$lostTicks;

    @Unique
    private UUID pvo$targetUuid;

    @Unique
    private Vec3 pvo$initialDirection;

    @Unique
    private Vec3 pvo$lastAimPoint;

    @Unique
    private boolean pvo$hasFireworkExplosions() {
        ItemStack stack = this.getItem();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        return fireworks != null && !fireworks.explosions().isEmpty();
    }

    @Unique
    private boolean pvo$computeCandidate() {
        // 1. Игнорируем фейерверки, используемые для разгона на элитрах
        if (this.attachedToEntity != null) {
            return false;
        }
        // 2. Игнорируем обычные салюты (с руки, из раздатчиков и т.д.)
        // ПВО интеллект активируется ТОЛЬКО если ракета выстрелена из арбалета
        if (!this.isShotAtAngle()) {
            return false;
        }
        // 3. Проверяем наличие пиротехнических зарядов (звездочек) в ракете
        return pvo$hasFireworkExplosions();
    }

    @Unique
    private LivingEntity pvo$getValidTarget(ServerLevel serverLevel, Vec3 axis) {
        if (this.pvo$targetUuid == null) {
            return null;
        }
        Entity found = serverLevel.getEntity(this.pvo$targetUuid);
        if (!(found instanceof LivingEntity living) || !living.isAlive()) {
            return null;
        }
        if (living instanceof Player player) {
            if (!player.isFallFlying()) {
                return null;
            }
        } else if (!pvo$isAerialTarget(living)) {
            return null;
        }
        Vec3 origin = this.position();
        double distSq = living.position().distanceToSqr(origin);
        if (distSq > PvoConfig.TARGET_RANGE * PvoConfig.TARGET_RANGE) {
            return null;
        }
        Vec3 toTarget = living.position().subtract(origin);
        if (toTarget.lengthSqr() < 1.0E-6) {
            return living;
        }
        double cosAngle = axis.normalize().dot(toTarget.normalize());
        if (cosAngle < PvoConfig.CONE_COS) {
            return null;
        }
        return living;
    }

    @Unique
    private LivingEntity pvo$findTarget(ServerLevel serverLevel, Vec3 axis) {
        Vec3 origin = this.position();
        AABB box = new AABB(origin, origin).inflate(PvoConfig.TARGET_RANGE);
        Entity owner = this.getOwner();
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Player player : serverLevel.players()) {
            if (player == owner) {
                continue;
            }
            if (player.isSpectator()) {
                continue;
            }
            if (!player.isFallFlying()) {
                continue;
            }
            double distSq = player.position().distanceToSqr(origin);
            if (distSq > PvoConfig.TARGET_RANGE * PvoConfig.TARGET_RANGE) {
                continue;
            }
            Vec3 toTarget = player.position().subtract(origin);
            if (toTarget.lengthSqr() < 1.0E-6) {
                continue;
            }
            double cosAngle = axis.normalize().dot(toTarget.normalize());
            if (cosAngle < PvoConfig.CONE_COS) {
                continue;
            }
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player;
            }
        }
        for (LivingEntity aerial : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (aerial == owner) {
                continue;
            }
            if (!aerial.isAlive() || aerial.isRemoved()) {
                continue;
            }
            if (!pvo$isAerialTarget(aerial)) {
                continue;
            }
            double distSq = aerial.position().distanceToSqr(origin);
            if (distSq > PvoConfig.TARGET_RANGE * PvoConfig.TARGET_RANGE) {
                continue;
            }
            Vec3 toTarget = aerial.position().subtract(origin);
            if (toTarget.lengthSqr() < 1.0E-6) {
                continue;
            }
            double cosAngle = axis.normalize().dot(toTarget.normalize());
            if (cosAngle < PvoConfig.CONE_COS) {
                continue;
            }
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = aerial;
            }
        }
        return best;
    }

    /**
     * Цели ПВО среди мобов: всё, что держится в воздухе. Летучая мышь исключена
     * намеренно (её ловить бессмысленно), игроки обрабатываются отдельно —
     * только в режиме планирования на элитрах.
     */
    @Unique
    private static boolean pvo$isAerialTarget(LivingEntity entity) {
        return entity instanceof Phantom
                || entity instanceof Vex
                || entity instanceof Ghast
                || entity instanceof HappyGhast
                || entity instanceof WitherBoss
                || entity instanceof EnderDragon
                || entity instanceof Allay
                || entity instanceof Blaze;
    }

    @Unique
    private Vec3 pvo$computeAimPoint(LivingEntity target) {
        Vec3 origin = this.position();
        double distance = target.position().distanceTo(origin);
        double currentSpeed = Math.max(this.getDeltaMovement().length(), 0.1);
        double leadTicks = Mth.clamp(distance / currentSpeed, 0.0, PvoConfig.MAX_LEAD_TICKS);
        Vec3 aim = target.position().add(target.getDeltaMovement().scale(leadTicks));
        if (Double.isNaN(aim.x) || Double.isNaN(aim.y) || Double.isNaN(aim.z)) {
            return target.position();
        }
        return aim;
    }

    @Unique
    private static Vec3 pvo$rotateTowards(Vec3 current, Vec3 desired, double maxAngleRad) {
        Vec3 from = current.normalize();
        Vec3 to = desired.normalize();
        double dot = Mth.clamp(from.dot(to), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle <= 1.0E-6) {
            return to;
        }
        Vec3 axis = from.cross(to);
        if (axis.lengthSqr() < 1.0E-10) {
            return to;
        }
        axis = axis.normalize();
        double rotAngle = Math.min(angle, maxAngleRad);
        double cosA = Math.cos(rotAngle);
        double sinA = Math.sin(rotAngle);
        Vec3 rotated = from.scale(cosA)
                .add(axis.cross(from).scale(sinA))
                .add(axis.scale(axis.dot(from) * (1.0 - cosA)));
        return rotated.normalize();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void pvo$onTick(CallbackInfo ci) {
        if (this.level().isClientSide()) {
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (this.pvo$exploded) {
            return;
        }
        if (!this.pvo$initialized) {
            this.pvo$initialized = true;
            this.pvo$isCandidate = this.pvo$computeCandidate();
            if (this.pvo$isCandidate) {
                this.pvo$active = true;
                this.lifetime = PvoConfig.MAX_LIFETIME_TICKS;

                // Отключаем гравитацию, чтобы ракета летела по идеальной прямой без проседания
                this.setNoGravity(false);

                // Берем ГОТОВЫЙ вектор полёта, который арбалет уже задал ракете при выстреле!
                Vec3 vel = this.getDeltaMovement();
                if (vel.lengthSqr() > 1.0E-6) {
                    this.pvo$initialDirection = vel.normalize();
                    this.pvo$initialSpeed = vel.length(); // Сохраняем родную высокую скорость выстрела арбалета
                } else {
                    Entity owner = this.getOwner();
                    this.pvo$initialDirection = (owner != null) ? owner.getLookAngle() : new Vec3(0.0, 0.0, 1.0);
                    this.pvo$initialSpeed = PvoConfig.INITIAL_SPEED;
                    this.setDeltaMovement(this.pvo$initialDirection.scale(this.pvo$initialSpeed));
                }
            }
        }
        if (!this.pvo$isCandidate || !this.pvo$active) {
            return;
        }
        if (!this.pvo$hasFireworkExplosions()) {
            if (PvoConfig.DEBUG) {
                pvo$LOGGER.info("PVO пропущен для невалидного фейерверка");
            }
            this.discard();
            return;
        }
        Level lvl = this.level();
        if (!(lvl instanceof ServerLevel serverLevel)) {
            return;
        }
        this.pvo$age++;

        // Проверка на истечение максимального времени жизни (самоликвидация)
        if (this.pvo$age >= PvoConfig.MAX_LIFETIME_TICKS) {
            if (PvoConfig.DEBUG) {
                pvo$LOGGER.info("взрыв по таймеру самоликвидации");
            }
            ((FireworkRocketEntityAccessor) this).pvo$invokeExplode(serverLevel);
            this.pvo$exploded = true;
            return;
        }

        Vec3 axis = this.getDeltaMovement().lengthSqr() > 1.0E-6 ? this.getDeltaMovement() : this.pvo$initialDirection;
        LivingEntity target = this.pvo$getValidTarget(serverLevel, axis);
        if (target == null && this.pvo$targetUuid != null) {
            this.pvo$targetUuid = null;
            this.pvo$lostTicks = PvoConfig.LOST_TARGET_MEMORY_TICKS;
            if (PvoConfig.DEBUG) {
                pvo$LOGGER.info("цель потеряна");
            }
        }
        if (target == null && this.pvo$targetUuid == null && this.pvo$age % PvoConfig.SEARCH_INTERVAL_TICKS == 0) {
            LivingEntity found = this.pvo$findTarget(serverLevel, axis);
            if (found != null) {
                this.pvo$targetUuid = found.getUUID();
                target = found;
                this.pvo$lostTicks = 0;
                if (PvoConfig.DEBUG) {
                    pvo$LOGGER.info("цель захвачена");
                }
            }
        }
        Vec3 desiredDir;
        if (target != null) {
            Vec3 aimPoint = this.pvo$computeAimPoint(target);
            this.pvo$lastAimPoint = aimPoint;
            Vec3 toAim = aimPoint.subtract(this.position());
            desiredDir = toAim.lengthSqr() > 1.0E-6 ? toAim.normalize() : this.pvo$initialDirection;
        } else if (this.pvo$lostTicks > 0 && this.pvo$lastAimPoint != null) {
            Vec3 toAim = this.pvo$lastAimPoint.subtract(this.position());
            desiredDir = toAim.lengthSqr() > 1.0E-6 ? toAim.normalize() : this.pvo$initialDirection;
            this.pvo$lostTicks--;
        } else {
            // Без цели ракета летит СТРОГО по ровной траектории арбалетного выстрела
            desiredDir = this.pvo$initialDirection;
        }
// === НОВАЯ ЛОГИКА РАЗГОНА С УЧЕТОМ ИНЕРЦИИ САМОЛЕТА/ЭЛИТРЫ ===
        double currentSpeedBeforeTurn = Math.max(this.getDeltaMovement().length(), 1.0E-4);
        double allowedTurn = Math.min(PvoConfig.MAX_TURN_RAD_PER_TICK, PvoConfig.LATERAL_ACCEL / currentSpeedBeforeTurn);
        Vec3 newDir = pvo$rotateTowards(this.getDeltaMovement(), desiredDir, allowedTurn);

        double targetSpeed;
        if (this.pvo$age <= PvoConfig.THRUST_DURATION_TICKS) {
            // Абсолютный лимит: двигатель разгоняет ракету до MAX_SPEED,
            // но если игрок летел быстрее MAX_SPEED, ракета сохраняет инерцию игрока (не тормозит)
            double effectiveMaxSpeed = Math.max(this.pvo$initialSpeed, PvoConfig.MAX_SPEED);
            double progress = this.pvo$age / (double) PvoConfig.THRUST_DURATION_TICKS;

            targetSpeed = this.pvo$initialSpeed + (effectiveMaxSpeed - this.pvo$initialSpeed) * progress;
        } else {
            targetSpeed = currentSpeedBeforeTurn * DRAG_AFTER_THRUST;
        }

        targetSpeed = Math.max(0.0, targetSpeed);
        Vec3 newVelocity = newDir.scale(targetSpeed);
        this.setDeltaMovement(newVelocity);
        // --- Спавн эффектов шлейфа ---
        if (this.pvo$age <= PvoConfig.THRUST_DURATION_TICKS) {
            // Фаза разгона: темный густой дым
            serverLevel.sendParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE, // Тип партикла
                    true, true,
                    this.getX(), this.getY(), this.getZ(), // Координаты
                    2,     // Количество партиклов за тик
                    0.02, 0.02, 0.02, // Разброс по X, Y, Z
                    0.01   // Скорость разлета
            );
        } else {
            // Фаза движения по инерции: белый пар
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    true, true,
                    this.getX(), this.getY(), this.getZ(),
                    1,     // Количество партиклов за тик
                    0.05, 0.05, 0.05,
                    0.01
            );
        }
        if (this.pvo$age >= PvoConfig.ARM_DELAY_TICKS) {
            boolean shouldExplode = false;
            boolean byProximity = false;
            if (target != null) {
                Vec3 origin = this.position();
                double distSq = target.position().distanceToSqr(origin);
                if (distSq <= PvoConfig.PROXIMITY_RADIUS * PvoConfig.PROXIMITY_RADIUS) {
                    shouldExplode = true;
                    byProximity = true;
                } else {
                    Vec3 nextPos = origin.add(newVelocity);
                    double nextDistSq = target.position().distanceToSqr(nextPos);
                    if (nextDistSq <= PvoConfig.PROXIMITY_RADIUS * PvoConfig.PROXIMITY_RADIUS) {
                        shouldExplode = true;
                        byProximity = true;
                    }
                }
            }
            if (!shouldExplode && (this.horizontalCollision || this.verticalCollision)) {
                shouldExplode = true;
            }
            if (shouldExplode) {
                if (PvoConfig.DEBUG) {
                    if (byProximity) {
                        pvo$LOGGER.info("взрыв по радиовзрывателю");
                    } else {
                        pvo$LOGGER.info("взрыв по блоку");
                    }
                }
                ((FireworkRocketEntityAccessor) this).pvo$invokeExplode(serverLevel);
                this.pvo$exploded = true;
            }
        }
    }
}