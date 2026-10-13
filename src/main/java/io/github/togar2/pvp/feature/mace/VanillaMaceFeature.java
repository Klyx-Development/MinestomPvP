package io.github.togar2.pvp.feature.mace;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.metadata.animal.tameable.TameableAnimalMeta;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.server.network.packet.server.play.WorldEventPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.property.ServerProperties;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.scoreboard.Team;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.WeightedList;
import net.minestom.server.worldevent.WorldEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla implementation of {@link MaceFeature}
 */
public class VanillaMaceFeature implements MaceFeature {
	public static final DefinedFeature<VanillaMaceFeature> DEFINED = new DefinedFeature<>(
			FeatureType.MACE, VanillaMaceFeature::new,
			FeatureType.FALL
	);

	public static final double SMASH_ATTACK_FALL_THRESHOLD = 1.5;
	public static final double SMASH_ATTACK_HEAVY_THRESHOLD = 5.0;
	public static final double SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5;
	public static final double SMASH_ATTACK_KNOCKBACK_POWER = 0.7;

	public static final float WIND_BURST_RADIUS = 3.5f;

	private final FeatureConfiguration configuration;

	private FallFeature fallFeature;

	public VanillaMaceFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.fallFeature = configuration.get(FeatureType.FALL);
	}

	@Override
	public boolean canSmashAttack(LivingEntity attacker) {
		return attacker.getItemInMainHand().material() == Material.MACE
				&& fallFeature.getFallDistance(attacker) > SMASH_ATTACK_FALL_THRESHOLD
				&& !attacker.isFlyingWithElytra();
	}

	@Override
	public float getSmashDamageBonus(LivingEntity attacker, Entity target) {
		if (!canSmashAttack(attacker)) return 0;

		double fallDistance = fallFeature.getFallDistance(attacker);
		double damage;
		if (fallDistance <= 3.0) {
			damage = 4.0 * fallDistance;
		} else if (fallDistance <= 8.0) {
			damage = 12.0 + 2.0 * (fallDistance - 3.0);
		} else {
			damage = 22.0 + fallDistance - 8.0;
		}

		// Apply density
		int density = getLevel(attacker.getItemInMainHand(), Enchantment.DENSITY);
		damage += 0.5 * density * fallDistance;

		return (float) damage;
	}

	@Override
	public void onSmashAttack(LivingEntity attacker, LivingEntity target) {
		if (!canSmashAttack(attacker)) return;

		int tps = ServerProperties.SERVER_TICKS_PER_SECOND.get();

		// Stop the attacker's fall and ignore fall damage until they land below the impact position
		if (attacker instanceof CombatPlayer custom) {
			custom.setVelocityNoUpdate(velocity -> velocity.withY(0.01 * tps));
			custom.sendImmediateVelocityUpdate();
		} else {
			attacker.setVelocity(attacker.getVelocity().withY(0.01 * tps));
		}

		fallFeature.ignoreFallDamageFromImpulse(attacker, calculateImpactPosition(attacker));

		double fallDistance = fallFeature.getFallDistance(attacker);
		Sound.Source source = attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE;
		if (target.isOnGround()) {
			fallFeature.setExtraFallParticles(attacker, true);

			SoundEvent sound = fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD ?
					SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvent.ITEM_MACE_SMASH_GROUND;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(sound, source, 1.0f, 1.0f), attacker);
		} else {
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ITEM_MACE_SMASH_AIR, source,
					1.0f, 1.0f
			), attacker);
		}

		applySmashKnockback(attacker, target, fallDistance);

		// Apply wind burst
		int windBurst = getLevel(attacker.getItemInMainHand(), Enchantment.WIND_BURST);
		if (windBurst > 0) {
			applyWindBurst(attacker, windBurst);
		}

		fallFeature.resetFallDistance(attacker);
	}

	protected Point calculateImpactPosition(LivingEntity attacker) {
		Point impactPosition = fallFeature.getImpulseImpactPosition(attacker);
		return impactPosition != null && impactPosition.y() <= attacker.getPosition().y() ?
				impactPosition : attacker.getPosition();
	}

	protected void applySmashKnockback(LivingEntity attacker, LivingEntity target, double fallDistance) {
		Instance instance = target.getInstance();
		Pos targetPosition = target.getPosition();

		target.sendPacketToViewersAndSelf(new WorldEventPacket(
				WorldEvent.PARTICLES_SMASH_ATTACK.id(),
				targetPosition.sub(0, 0.2, 0).asVec().apply(Vec.Operator.FLOOR),
				750, false
		));

		int tps = ServerProperties.SERVER_TICKS_PER_SECOND.get();
		for (Entity entity : instance.getNearbyEntities(targetPosition, SMASH_ATTACK_KNOCKBACK_RADIUS + 2)) {
			if (!(entity instanceof LivingEntity nearby)) continue;
			if (!shouldReceiveSmashKnockback(attacker, target, nearby)) continue;

			Vec direction = nearby.getPosition().sub(targetPosition).asVec();
			double knockbackPower = (SMASH_ATTACK_KNOCKBACK_RADIUS - direction.length()) * SMASH_ATTACK_KNOCKBACK_POWER
					* (fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD ? 2 : 1)
					* (1.0 - nearby.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE));
			if (knockbackPower <= 0) continue;

			Vec knockback = direction.normalize().mul(knockbackPower).withY(SMASH_ATTACK_KNOCKBACK_POWER).mul(tps);
			if (nearby instanceof CombatPlayer custom) {
				custom.setVelocityNoUpdate(velocity -> velocity.add(knockback));
				custom.sendImmediateVelocityUpdate();
			} else {
				nearby.setVelocity(nearby.getVelocity().add(knockback));
			}
		}
	}

	protected boolean shouldReceiveSmashKnockback(LivingEntity attacker, LivingEntity target, LivingEntity nearby) {
		if (nearby == attacker || nearby == target) return false;
		if (nearby instanceof Player player) {
			if (player.getGameMode() == GameMode.SPECTATOR) return false;
			if (player.getGameMode() == GameMode.CREATIVE && player.isFlying()) return false;
		}

		Team team = attacker.getTeam();
		if (team != null && team == nearby.getTeam()) return false;

		// doesn't seem right to not knockback the *target*'s pets
        // doing that for the attacker's pets makes more sense in my head?
        // I can't find a bug report about this though, maybe it's intentional, so I'll just leave it
		if (nearby.getEntityMeta() instanceof TameableAnimalMeta meta
				&& meta.isTamed() && target.getUuid().equals(meta.getOwner())) return false;

		if (nearby.getEntityType() == EntityType.ARMOR_STAND
				&& nearby.getEntityMeta() instanceof ArmorStandMeta meta && meta.isMarker()) return false;

		return target.getPosition().distanceSquared(nearby.getPosition())
				<= SMASH_ATTACK_KNOCKBACK_RADIUS * SMASH_ATTACK_KNOCKBACK_RADIUS;
	}

	protected void applyWindBurst(LivingEntity attacker, int level) {
		Instance instance = attacker.getInstance();
		Pos center = attacker.getPosition();
		double knockbackMultiplier = getWindBurstKnockbackMultiplier(level);
		double doubleRadius = WIND_BURST_RADIUS * 2;
		int tps = ServerProperties.SERVER_TICKS_PER_SECOND.get();

		Map<Player, Vec> playerKnockback = new HashMap<>();
		for (Entity entity : instance.getNearbyEntities(center, doubleRadius)) {
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

			} else {
				entity.setVelocity(entity.getVelocity().add(knockback.mul(tps)));
			}
		}

		Chunk chunk = instance.getChunkAt(center);
		if (chunk == null) return;

		for (Player player : chunk.getViewers()) {
			player.sendPacket(new ExplosionPacket(
					center, WIND_BURST_RADIUS, 0, playerKnockback.getOrDefault(player, Vec.ZERO),
					Particle.GUST_EMITTER_LARGE, SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, WeightedList.of()
			));
		}
	}

	protected double getWindBurstKnockbackMultiplier(int level) {
		return switch (level) {
			case 1 -> 1.2;
			case 2 -> 1.75;
			case 3 -> 2.2;
			default -> 1.5 + 0.35 * (level - 1);
		};
	}

	private static int getLevel(ItemStack stack, RegistryKey<Enchantment> enchantment) {
		EnchantmentList enchantments = stack.get(DataComponents.ENCHANTMENTS);
		return enchantments == null ? 0 : enchantments.level(enchantment);
	}
}
