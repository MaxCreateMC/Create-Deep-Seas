package com.maxenonyme.createsubmarine.submarine.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BallastTankItem extends BlockItem {
    private static final int MAX_LAYER = 64;

    public BallastTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction())
            tryMultiPlace(context);
        return result;
    }

    private void tryMultiPlace(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown())
            return;
        Direction face = context.getClickedFace();
        if (!face.getAxis().isVertical())
            return;

        Level level = context.getLevel();
        if (level.isClientSide())
            return;

        BlockPos placed = context.getClickedPos();
        BlockPos support = placed.relative(face.getOpposite());
        if (!(level.getBlockState(support).getBlock() instanceof BallastTankBlock))
            return;

        ItemStack stack = context.getItemInHand();
        boolean free = player.getAbilities().instabuild;
        for (BlockPos source : layerAround(level, support)) {
            BlockPos target = source.relative(face);
            if (target.equals(placed))
                continue;
            if (!level.getBlockState(target).canBeReplaced())
                continue;
            if (!free && stack.isEmpty())
                break;
            level.setBlock(target, stateFor(level, target), Block.UPDATE_ALL);
            if (!free)
                stack.shrink(1);
        }
    }

    private BlockState stateFor(Level level, BlockPos pos) {
        return getBlock().defaultBlockState()
                .setValue(BallastTankBlock.TOP, !isTank(level, pos.above()))
                .setValue(BallastTankBlock.BOTTOM, !isTank(level, pos.below()));
    }

    private static boolean isTank(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BallastTankBlock;
    }

    private static List<BlockPos> layerAround(Level level, BlockPos origin) {
        List<BlockPos> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin.asLong());
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < MAX_LAYER) {
            BlockPos current = queue.poll();
            found.add(current);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                if (isTank(level, next) && seen.add(next.asLong()))
                    queue.add(next);
            }
        }
        return found;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (Screen.hasShiftDown()) {
            tooltipComponents.add(Component.empty());

            int createYellow = 0xEBC255;
            int createOrange = 0xEE9246;

            String part1 = Component.translatable("item.create_submarine.ballast_tank.tooltip.summary_part1").getString();
            String part2 = Component.translatable("item.create_submarine.ballast_tank.tooltip.summary_part2").getString();

            String[] linesPart1 = part1.split("\n");
            for (int i = 0; i < linesPart1.length; i++) {
                Component lineComp = Component.literal(linesPart1[i]).withStyle(style -> style.withColor(createYellow));
                if (i == linesPart1.length - 1) {
                    lineComp = lineComp.copy().append(Component.translatable("block.create_submarine.ballast_vent")
                                       .withStyle(style -> style.withColor(createOrange)))
                                       .append(Component.literal(part2).withStyle(style -> style.withColor(createYellow)));
                }
                tooltipComponents.add(lineComp);
            }

            tooltipComponents.add(Component.empty());

            addTranslatableLines(tooltipComponents, "item.create_submarine.ballast_tank.tooltip.goggles", createYellow);
        } else {
            tooltipComponents.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    public static void addTranslatableLines(List<Component> tooltip, String key, int colorHex) {
        String translated = Component.translatable(key).getString();
        for (String line : translated.split("\n")) {
            tooltip.add(Component.literal(line).withStyle(style -> style.withColor(colorHex)));
        }
    }
}