package com.maxenonyme.createsubmarine.submarine.effect;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
public class SuffocationEffect extends MobEffect {
    private static final ResourceKey<DamageType> DAMAGE_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "suffocation"));
    public SuffocationEffect() {
        super(MobEffectCategory.HARMFUL, 0x333333);
    }
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
        entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_KEY)), 1.0f + amplifier);
        return true;
    }
}
