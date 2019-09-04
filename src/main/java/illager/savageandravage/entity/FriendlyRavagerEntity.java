package illager.savageandravage.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.monster.RavagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Random;

public class FriendlyRavagerEntity extends RavagerEntity implements IJumpingMount {
    protected static final IAttribute JUMP_STRENGTH = (new RangedAttribute((IAttribute) null, "horse.jumpStrength", 0.7D, 0.0D, 2.0D)).setDescription("Jump Strength").setShouldWatch(true);
    protected boolean horseJumping;
    protected float jumpPower;
    protected boolean allowStandSliding;
    protected int gallopTime;

    public void setHorseJumping(boolean jumping) {
        this.horseJumping = jumping;
    }

    public boolean isHorseJumping() {
        return this.horseJumping;
    }

    public double getHorseJumpStrength() {
        return this.getAttribute(JUMP_STRENGTH).getValue();
    }

    @SuppressWarnings("unchecked")
    public FriendlyRavagerEntity(EntityType<? extends RavagerEntity> type, World worldIn) {
        super(type, worldIn);
        this.experienceValue = 10;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomWalkingGoal(this, 0.45D));
        this.goalSelector.addGoal(2, new LookRandomlyGoal(this));

    }

    @Override
    protected void registerAttributes() {
        super.registerAttributes();
        this.getAttributes().registerAttribute(JUMP_STRENGTH);
        this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100.0d);
        this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3d);
    }


    public static boolean spawnEntity(EntityType<? extends FriendlyRavagerEntity> entity, IWorld world, SpawnReason spawnReason, BlockPos pos, Random random) {
        return world.getBlockState(pos.down()).getBlock() == Blocks.GRASS_BLOCK && world.getLightSubtracted(pos, 0) > 8;
    }

    /**
     * returns true if all the conditions for steering the entity are met. For pigs, this is true if it is being ridden
     * by a player and the player is holding a carrot-on-a-stick
     */

    @Override
    public boolean canBeSteered() {
        return this.getControllingPassenger() instanceof LivingEntity;
    }

    protected void mountTo(PlayerEntity player) {
        if (!this.world.isRemote) {
            player.rotationYaw = this.rotationYaw;
            player.rotationPitch = this.rotationPitch;
            player.startRiding(this);
        }

    }

    public boolean processInteract(PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        boolean flag = !itemstack.isEmpty();
        if (flag && itemstack.getItem() instanceof SpawnEggItem) {
            return super.processInteract(player, hand);
        } else {
            if (this.isChild()) {
                return super.processInteract(player, hand);
            } else {
                this.mountTo(player);
                return true;
            }
        }
    }

    /**
     * Returns true if this entity should push and be pushed by other entities when colliding.
     */

    @Override
    public boolean canBePushed() {
        return !this.isBeingRidden();
    }

    /**
     * Dead and sleeping entities cannot move
     */

    @Override
    protected boolean isMovementBlocked() {
        return super.isMovementBlocked() && this.isBeingRidden();
    }

    /**
     * For vehicles, the first passenger is generally considered the controller and "drives" the vehicle. For example,
     * Pigs, Horses, and Boats are generally "steered" by the controlling passenger.
     */
    @Nullable
    public Entity getControllingPassenger() {
        return this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
    }

    @Override
    public void updatePassenger(Entity passenger) {
        super.updatePassenger(passenger);
        if (passenger instanceof MobEntity) {
            MobEntity mobentity = (MobEntity) passenger;
            this.renderYawOffset = mobentity.renderYawOffset;
        }

    }

    @Override
    public void travel(Vec3d vector) {
        if (this.isAlive()) {
            if (this.isBeingRidden() && this.canBeSteered()) {
                LivingEntity livingentity = (LivingEntity) this.getControllingPassenger();
                this.rotationYaw = livingentity.rotationYaw;
                this.prevRotationYaw = this.rotationYaw;
                this.rotationPitch = livingentity.rotationPitch * 0.5F;
                this.setRotation(this.rotationYaw, this.rotationPitch);
                this.renderYawOffset = this.rotationYaw;
                this.rotationYawHead = this.renderYawOffset;
                float strafe = livingentity.moveStrafing * 0.5F;
                float forward = livingentity.moveForward;
                if (forward <= 0.0F) {
                    forward *= 0.25F;
                    this.gallopTime = 0;
                }

                if (this.onGround && this.jumpPower == 0.0F && !this.allowStandSliding) {
                    strafe = livingentity.moveStrafing / 3F;
                    forward = livingentity.moveForward / 2F;
                }

                if (this.jumpPower > 0.0F && !this.isHorseJumping() && this.onGround) {
                    double d0 = this.getHorseJumpStrength() * (double) this.jumpPower;
                    double d1;
                    if (this.isPotionActive(Effects.JUMP_BOOST)) {
                        d1 = d0 + (double) ((float) (this.getActivePotionEffect(Effects.JUMP_BOOST).getAmplifier() + 1) * 0.1F);
                    } else {
                        d1 = d0;
                    }

                    Vec3d vec3d = this.getMotion();
                    this.setMotion(vec3d.x, d1, vec3d.z);
                    this.setHorseJumping(true);
                    this.isAirBorne = true;
                    if (forward > 0.0F) {
                        float f2 = MathHelper.sin(this.rotationYaw * ((float) Math.PI / 180F));
                        float f3 = MathHelper.cos(this.rotationYaw * ((float) Math.PI / 180F));
                        this.setMotion(this.getMotion().add((double) (-0.4F * f2 * this.jumpPower), 0.0D, (double) (0.4F * f3 * this.jumpPower)));
                    }

                    this.jumpPower = 0.0F;
                }

                this.jumpMovementFactor = this.getAIMoveSpeed() * 0.1F;
                if (this.canPassengerSteer()) {
                    this.setAIMoveSpeed((float) this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getValue() * 0.6F);
                    super.travel(new Vec3d((double) strafe, vector.y, (double) forward));
                } else if (livingentity instanceof PlayerEntity) {
                    this.setMotion(Vec3d.ZERO);
                }

                if (this.onGround) {
                    this.jumpPower = 0.0F;
                    this.setHorseJumping(false);
                }

            } else {
                this.jumpMovementFactor = 0.02F;
                super.travel(vector);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void setJumpPower(int jumpPowerIn) {
        if (jumpPowerIn < 0) {
            jumpPowerIn = 0;
        } else {
            this.allowStandSliding = true;
        }

        if (jumpPowerIn >= 90) {
            this.jumpPower = 1.0F;
        } else {
            this.jumpPower = 0.4F + 0.4F * (float) jumpPowerIn / 90.0F;
        }

    }

    public boolean canJump() {
        return true;
    }

    @Override
    public void handleStartJump(int p_184775_1_) {

    }

    @Override
    public void handleStopJump() {

    }

    public void fall(float distance, float damageMultiplier) {
        if (distance > 1.0F) {
            this.playSound(SoundEvents.ENTITY_RAVAGER_STEP, 0.4F, 1.0F);
        }

        int i = MathHelper.ceil((distance * 0.5F - 3.0F) * damageMultiplier);
        if (i > 0) {
            this.attackEntityFrom(DamageSource.FALL, (float) i);
            if (this.isBeingRidden()) {
                for (Entity entity : this.getRecursivePassengers()) {
                    entity.attackEntityFrom(DamageSource.FALL, (float) i);
                }
            }

            BlockState blockstate = this.world.getBlockState(new BlockPos(this.posX, this.posY - 0.2D - (double) this.prevRotationYaw, this.posZ));
            if (!blockstate.isAir() && !this.isSilent()) {
                SoundType soundtype = blockstate.getSoundType();
                this.world.playSound((PlayerEntity) null, this.posX, this.posY, this.posZ, soundtype.getStepSound(), this.getSoundCategory(), soundtype.getVolume() * 0.5F, soundtype.getPitch() * 0.75F);
            }

        }
    }

}
