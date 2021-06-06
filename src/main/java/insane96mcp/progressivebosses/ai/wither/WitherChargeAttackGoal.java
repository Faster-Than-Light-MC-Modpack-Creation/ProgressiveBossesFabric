package insane96mcp.progressivebosses.ai.wither;

import insane96mcp.insanelib.utils.RandomHelper;
import insane96mcp.progressivebosses.base.Strings;
import insane96mcp.progressivebosses.modules.wither.feature.AttackFeature;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class WitherChargeAttackGoal extends Goal {
	private final WitherEntity wither;
	private LivingEntity target;
	private Vector3d targetPos;

	public WitherChargeAttackGoal(WitherEntity wither) {
		this.wither = wither;
		this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK, Flag.TARGET));
	}

	/**
	 * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
	 * method as well.
	 */
	public boolean shouldExecute() {
		if (this.wither.getInvulTime() != AttackFeature.Consts.CHARGE_ATTACK_TICK_START)
			return false;
		CompoundNBT witherTags = wither.getPersistentData();
		return witherTags.contains(Strings.Tags.CHARGE_ATTACK);
	}

	public void startExecuting() {
		this.wither.getNavigator().clearPath();
		for (int h = 0; h < 3; h++)
			this.wither.updateWatchedTargetId(h, 0);
	}

	/**
	 * Returns whether an in-progress EntityAIBase should continue executing
	 */
	public boolean shouldContinueExecuting() {
		return this.wither.getInvulTime() > 0;
	}

	/**
	 * Reset the task's internal state. Called when this task is interrupted by another one
	 */
	public void resetTask() {
		this.target = null;
		CompoundNBT witherTags = wither.getPersistentData();
		witherTags.remove(Strings.Tags.CHARGE_ATTACK);
		this.wither.setMotion(this.wither.getMotion().mul(0.1d, 0.1d, 0.1d));
	}

	/**
	 * Keep ticking a continuous task that has already been started
	 */
	public void tick() {
		if (this.wither.getInvulTime() > AttackFeature.Consts.CHARGE_ATTACK_TICK_CHARGE)
			this.wither.setMotion(Vector3d.ZERO);

		if (this.wither.getInvulTime() == AttackFeature.Consts.CHARGE_ATTACK_TICK_START)
			this.wither.world.playSound(null, this.wither.getPosition(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 5.0f, 2.0f);
		else if (this.wither.getInvulTime() == AttackFeature.Consts.CHARGE_ATTACK_TICK_CHARGE) {
			this.target = GetRandomNearPlayer(this.wither);
			if (target != null) {
				this.targetPos = this.target.getPositionVec();
				this.wither.world.playSound(null, new BlockPos(this.targetPos), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 4.0f, 2.0f);
			}
			else {
				this.wither.world.createExplosion(this.wither, this.wither.getPosX(), this.wither.getPosY() + 1.75d, this.wither.getPosZ(), 6f, Explosion.Mode.DESTROY);
				this.wither.setInvulTime(0);
			}
			//this.wither.world.playSound(null, new BlockPos(this.targetPos), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 4.0f, 0.8f);
		}
		else if (this.wither.getInvulTime() < AttackFeature.Consts.CHARGE_ATTACK_TICK_CHARGE) {
			double mult = 100d / this.wither.getInvulTime();
			Vector3d diff = this.targetPos.subtract(this.wither.getPositionVec()).normalize().mul(mult, mult, mult);
			this.wither.setMotion(diff.x, diff.y * 0.6, diff.z);
			this.wither.getLookController().setLookPosition(this.targetPos);
			AxisAlignedBB axisAlignedBB = new AxisAlignedBB(this.wither.getPosX() - 2, this.wither.getPosY() - 2, this.wither.getPosZ() - 2, this.wither.getPosX() + 2, this.wither.getPosY() + 6, this.wither.getPosZ() + 2);
			Stream<BlockPos> blocks = BlockPos.getAllInBox(axisAlignedBB);
			AtomicBoolean hasBrokenBlocks = new AtomicBoolean(false);
			blocks.forEach(blockPos -> {
				BlockState state = wither.world.getBlockState(blockPos);
				if (state.canEntityDestroy(wither.world, blockPos, wither) && net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(wither, blockPos, state) && !state.getBlock().equals(Blocks.AIR)) {
					TileEntity tileentity = state.hasTileEntity() ? this.wither.world.getTileEntity(blockPos) : null;
					LootContext.Builder lootcontext$builder = (new LootContext.Builder((ServerWorld)this.wither.world)).withRandom(this.wither.world.rand).withParameter(LootParameters.field_237457_g_, Vector3d.copyCentered(blockPos)).withParameter(LootParameters.TOOL, ItemStack.EMPTY).withNullableParameter(LootParameters.BLOCK_ENTITY, tileentity);
					state.getDrops(lootcontext$builder).forEach(itemStack -> {
						ItemEntity itemEntity = new ItemEntity(this.wither.world, blockPos.getX() + .5d, blockPos.getY() + .5d, blockPos.getZ() + .5d, itemStack);
						CompoundNBT compound = new CompoundNBT();
						compound.putShort("Age", (short)(6000 - 1200));
						itemEntity.readAdditional(compound);
						this.wither.world.addEntity(itemEntity);
					});
					wither.world.setBlockState(blockPos, Blocks.AIR.getDefaultState());
					hasBrokenBlocks.set(true);
				}
			});

			if (hasBrokenBlocks.get() /*&& this.wither.getInvulTime() % 4 == 0*/)
				this.wither.world.playSound(null, new BlockPos(this.targetPos), SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.HOSTILE, 1.0f, 0.75f);

			axisAlignedBB = axisAlignedBB.grow(0.75d);
			this.wither.world.getLoadedEntitiesWithinAABB(LivingEntity.class, axisAlignedBB).forEach(entity -> {
				entity.attackEntityFrom(new EntityDamageSource("charge_attack", this.wither), 10f);
				entity.applyKnockback(1f, this.wither.getPosX() - entity.getPosX(), this.wither.getPosZ() - entity.getPosZ());
			});
		}
		//If the wither's charging and is 2 blocks from the target point OR is about to finish the invulnerability time then prevent the explosion and stop the attack
		if ((this.wither.getInvulTime() < AttackFeature.Consts.CHARGE_ATTACK_TICK_CHARGE && this.wither.getInvulTime() > 0 && this.targetPos.squareDistanceTo(this.wither.getPositionVec()) < 4d) || this.wither.getInvulTime() == 1) {
			this.wither.setInvulTime(0);
		}
	}

	@Nullable
	public LivingEntity GetRandomNearPlayer(WitherEntity witherEntity) {
		List<ServerPlayerEntity> players = this.wither.world.getLoadedEntitiesWithinAABB(ServerPlayerEntity.class, this.wither.getBoundingBox().grow(64d));
		if (players.isEmpty())
			return null;
		int r = RandomHelper.getInt(this.wither.world.rand, 0, players.size());
		return players.get(r);
	}
}