package io.github.togar2.pvp.feature.fall;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Combat feature which manages the fall distance and fall damage of entities.
 * It may also apply this damage when needed.
 */
public interface FallFeature extends CombatFeature {
	FallFeature NO_OP = new FallFeature() {
		@Override
		public int getFallDamage(LivingEntity entity, double fallDistance) {
			return 0;
		}
		
		@Override
		public double getFallDistance(LivingEntity entity) {
			return 0;
		}
		
		@Override
		public void resetFallDistance(LivingEntity entity) {}
		
		@Override
		public void setExtraFallParticles(LivingEntity entity, boolean extraFallParticles) {}
	};
	
	int getFallDamage(LivingEntity entity, double fallDistance);
	
	double getFallDistance(LivingEntity entity);
	
	void resetFallDistance(LivingEntity entity);
	
	void setExtraFallParticles(LivingEntity entity, boolean extraFallParticles);

	default void ignoreFallDamageFromImpulse(LivingEntity entity, Point impactPosition) {}

	default @Nullable Point getImpulseImpactPosition(LivingEntity entity) {
		return null;
	}
}
