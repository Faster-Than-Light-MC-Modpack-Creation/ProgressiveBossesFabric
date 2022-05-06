package insane96mcp.progressivebosses.module.elderguardian.feature;

import insane96mcp.insanelib.base.Feature;
import insane96mcp.insanelib.base.Label;
import insane96mcp.insanelib.base.Module;
import insane96mcp.progressivebosses.setup.Config;
import insane96mcp.progressivebosses.setup.Strings;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Label(name = "Base", description = "Base feature for the Elder Guardian harder fights. Disabling this feature will disable the added sound when an Elder Guardian is killed.")
public class BaseFeature extends Feature {

	private final ForgeConfigSpec.ConfigValue<Boolean> adventureConfig;

	public boolean adventure = true;

	public BaseFeature(Module module) {
		super(Config.builder, module);
		this.pushConfig(Config.builder);
		this.adventureConfig = Config.builder
				.comment("If true, the player will not be able to break blocks when an Elder Guardian is nearby.")
				.define("Adventure mode", this.adventure);
		Config.builder.pop();
	}

	@Override
	public void loadConfig() {
		super.loadConfig();
		this.adventure = this.adventureConfig.get();
	}

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.player.level.isClientSide)
			return;

		if (!this.isEnabled())
			return;

		if (!this.adventure)
			return;

		if (event.player.tickCount % 20 != 0)
			return;

		if (!event.player.isAlive())
			return;

		ServerPlayer serverPlayer = (ServerPlayer) event.player;
		ServerLevel world = (ServerLevel) serverPlayer.level;

		CompoundTag nbt = serverPlayer.getPersistentData();
		boolean previouslyNearElderGuardian = nbt.getBoolean(Strings.Tags.PREVIOUSLY_NEAR_ELDER_GUARDIAN);
		boolean adventureMessage = nbt.getBoolean(Strings.Tags.ADVENTURE_MESSAGE);

		boolean nearElderGuardian = !world.getEntitiesOfClass(ElderGuardian.class, serverPlayer.getBoundingBox().inflate(32d)).isEmpty();
		nbt.putBoolean(Strings.Tags.PREVIOUSLY_NEAR_ELDER_GUARDIAN, nearElderGuardian);

		if (serverPlayer.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && nearElderGuardian) {
			serverPlayer.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
			serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float)GameType.ADVENTURE.getId()));
			if (!adventureMessage) {
				serverPlayer.sendMessage(new TranslatableComponent(Strings.Translatable.APPROACHING_ELDER_GUARDIAN), Util.NIL_UUID);
				nbt.putBoolean(Strings.Tags.ADVENTURE_MESSAGE, true);
			}
		}
		else if (serverPlayer.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && !nearElderGuardian && previouslyNearElderGuardian) {
			serverPlayer.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
			serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float)GameType.SURVIVAL.getId()));
		}
	}

	@SubscribeEvent
	public void onPlayerDeath(LivingDeathEvent event) {
		if (!this.isEnabled())
			return;

		if (!this.adventure)
			return;

		if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
			return;

		CompoundTag nbt = serverPlayer.getPersistentData();
		boolean previouslyNearElderGuardian = nbt.getBoolean(Strings.Tags.PREVIOUSLY_NEAR_ELDER_GUARDIAN);

		if (previouslyNearElderGuardian && serverPlayer.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
			serverPlayer.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
			serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float)GameType.SURVIVAL.getId()));
		}
	}

	@SubscribeEvent
	public void onExplosionDetonate(ExplosionEvent.Start event) {
		if (!this.isEnabled())
			return;

		if (!this.adventure)
			return;

		if (event.getExplosion().getExploder() == null)
			return;

		if (event.getExplosion().blockInteraction == Explosion.BlockInteraction.NONE)
			return;

		boolean nearElderGuardian = !event.getWorld().getEntitiesOfClass(ElderGuardian.class, event.getExplosion().getExploder().getBoundingBox().inflate(32d)).isEmpty();
		if (nearElderGuardian) {
			event.setCanceled(true);
			event.getWorld().explode(event.getExplosion().getExploder(), event.getExplosion().getPosition().x, event.getExplosion().getPosition().y, event.getExplosion().getPosition().z, event.getExplosion().radius, event.getExplosion().fire, Explosion.BlockInteraction.NONE);
		}
	}

	@SubscribeEvent
	public void onElderGuardianDeath(LivingDeathEvent event) {
		if (!this.isEnabled())
			return;

		if (!(event.getEntity() instanceof ElderGuardian elderGuardian))
			return;

		int elderGuardiansNearby = elderGuardian.level.getEntities(elderGuardian, elderGuardian.getBoundingBox().inflate(48d), entity -> entity instanceof ElderGuardian).size();
		if (elderGuardiansNearby == 0)
			return;

		elderGuardian.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 2f, 0.5f);
	}

	public static int getDeadElderGuardians(ElderGuardian elderGuardian) {
		int elderGuardiansNearby = elderGuardian.level.getEntities(elderGuardian, elderGuardian.getBoundingBox().inflate(48d), entity -> entity instanceof ElderGuardian).size();
		return 2 - elderGuardiansNearby;
	}
}
