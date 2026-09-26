package dev.steppedplayeranimations.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("stepped_player_animations");
    private static final String EMF = "entity_model_features";
    private static final String EMOTECRAFT = "emotecraft";
    private static final String PLAYER_ANIMATOR = "playeranimator";
    private static final String BENDY_LIB = "bendy-lib";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded(EMF) || !loader.isModLoaded(EMOTECRAFT)) {
            return false;
        }

        boolean apply;
        if (mixinClassName.endsWith("EmfBendyBridgeMixin")) {
            apply = loader.isModLoaded(PLAYER_ANIMATOR) && loader.isModLoaded(BENDY_LIB);
        } else if (mixinClassName.endsWith("EmfPlayerAnimatorFallbackMixin")) {
            apply = loader.isModLoaded(PLAYER_ANIMATOR);
        } else {
            apply = true;
        }

        if (apply) {
            LOGGER.info("Applying compatibility Mixin: {}", mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        }
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
