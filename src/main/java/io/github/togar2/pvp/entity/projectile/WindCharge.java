package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.utils.WindExplosion;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.property.ServerProperties;
import org.jetbrains.annotations.Nullable;

public class WindCharge extends CustomEntityProjectile {
	public static final float EXPLOSION_RADIUS = 1.2f;
	public static final double KNOCKBACK_MULTIPLIER = 1.22;
	public static final int NO_DEFLECT_TICKS = 5;

	private final FallFeature fallFeature;
	
	// Speed added every tick in blocks per tick
	private double accelerationPower = 0;

	public WindCharge(@Nullable Entity shooter, FallFeature fallFeature) {
		super(shooter, EntityType.WIND_CHARGE);
		this.fallFeature = fallFeature;

		setAerodynamics(new Aerodynamics(0, 1, 1));
		setNoGravity(true);
	}

	@Override
	public void tick(long time) {
		super.tick(time);
		if (isRemoved()) return;
		
		if (accelerationPower > 0 && !velocity.isZero()) {
			int tps = ServerProperties.SERVER_TICKS_PER_SECOND.get();
			velocity = velocity.add(velocity.normalize().mul(accelerationPower * tps));
		}

		if (isStuck()) {
			explode(position.sub(collisionDirection.mul(0.25)));
			remove();
			return;
		}

		if (instance != null && position.y() > instance.getCachedDimensionType().maxY() + 30) {
			explode(position);
			remove();
		}
	}

	@Override
	public boolean onHit(Entity entity) {
		((LivingEntity) entity).damage(new Damage(DamageType.WIND_CHARGE, this, getShooter(), null, 1));
		explode(position);

		return true;
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && entity.getEntityType() != EntityType.END_CRYSTAL;
	}

	public boolean deflect(Entity deflector) {
		if (getAliveTicks() < NO_DEFLECT_TICKS) return false;
		
		Vec direction = deflector.getPosition().direction();
		setVelocity(direction.mul(ServerProperties.SERVER_TICKS_PER_SECOND.get()));
		setView(
				(float) Math.toDegrees(Math.atan2(direction.x(), direction.z())),
				(float) Math.toDegrees(Math.atan2(direction.y(), Math.sqrt(direction.x() * direction.x() + direction.z() * direction.z())))
		);
		setShooter(deflector);
		accelerationPower = 0.1;
		
		return true;
	}
	
	protected void explode(Point position) {
		WindExplosion.explode(instance, position, EXPLOSION_RADIUS, KNOCKBACK_MULTIPLIER, this, fallFeature);
	}
}
