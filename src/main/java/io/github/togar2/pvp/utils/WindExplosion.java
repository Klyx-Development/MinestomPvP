package io.github.togar2.pvp.utils;

import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.property.ServerProperties;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.WeightedList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Wind explosions, used for wind charges and the wind burst enchantment.
 */
public final class WindExplosion {
	private WindExplosion() {}

	/**
	 * Creates a wind explosion.
	 *
	 * @param fallFeature if not null, players hit will ignore fall damage from the knockback from the explosion
	 */
	public static void explode(
			Instance instance, Point center, float radius, double knockbackMultiplier,
			@Nullable Entity source, @Nullable FallFeature fallFeature
	) {
		double doubleRadius = radius * 2;
		int tps = ServerProperties.SERVER_TICKS_PER_SECOND.get();

		Map<Player, Vec> playerKnockback = new HashMap<>();
		for (Entity entity : instance.getNearbyEntities(center, doubleRadius)) {
			if (entity == source) continue;

			double distance = entity.getPosition().distance(center) / doubleRadius;
			if (distance > 1.0) continue;

			if (entity instanceof Player player && (player.getGameMode() == GameMode.SPECTATOR
					|| (player.getGameMode() == GameMode.CREATIVE && player.isFlying()))) continue;

			Point origin = entity.getEntityType() == EntityType.TNT ?
					entity.getPosition() : entity.getPosition().add(0, entity.getEyeHeight(), 0);
			Vec direction = origin.sub(center).asVec().normalize();
			double exposure = VanillaExplosionSupplier.getExposure(center, entity);
			double resistance = entity instanceof LivingEntity living ?
					living.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE) : 0;

			Vec knockback = direction.mul((1.0 - distance) * exposure * knockbackMultiplier * (1.0 - resistance));
			if (entity instanceof Player player) {
				playerKnockback.put(player, knockback);
				if (player instanceof CombatPlayer custom) {
					custom.setVelocityNoUpdate(velocity -> velocity.add(knockback.mul(tps)));
				}

				if (fallFeature != null) fallFeature.ignoreFallDamageFromImpulse(player, player.getPosition());
			} else {
				entity.setVelocity(entity.getVelocity().add(knockback.mul(tps)));
			}
		}

		Chunk chunk = instance.getChunkAt(center);
		if (chunk == null) return;

		Particle particle = radius < 2 ? Particle.GUST_EMITTER_SMALL : Particle.GUST_EMITTER_LARGE;
		for (Player player : chunk.getViewers()) {
			player.sendPacket(new ExplosionPacket(
					center, radius, 0,
					playerKnockback.getOrDefault(player, Vec.ZERO),
					particle, SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST,
					WeightedList.of()
			));
		}
	}
}
