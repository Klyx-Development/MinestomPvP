package io.github.togar2.pvp.feature.mace;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;

/**
 * Combat feature which handle the mace and it's abilities.
 */
public interface MaceFeature extends CombatFeature {
	MaceFeature NO_OP = new MaceFeature() {
		@Override
		public boolean canSmashAttack(LivingEntity attacker) {
			return false;
		}

		@Override
		public float getSmashDamageBonus(LivingEntity attacker, Entity target) {
			return 0;
		}

		@Override
		public void onSmashAttack(LivingEntity attacker, LivingEntity target) {}
	};

	boolean canSmashAttack(LivingEntity attacker);

	float getSmashDamageBonus(LivingEntity attacker, Entity target);

	void onSmashAttack(LivingEntity attacker, LivingEntity target);
}
