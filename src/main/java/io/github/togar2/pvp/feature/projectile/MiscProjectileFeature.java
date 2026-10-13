package io.github.togar2.pvp.feature.projectile;

import io.github.togar2.pvp.feature.CombatFeature;

/**
 * Combat feature which handles throwing snowballs, eggs, ender pearls and wind charges.
 */
public interface MiscProjectileFeature extends CombatFeature {
	MiscProjectileFeature NO_OP = new MiscProjectileFeature() {};
}
