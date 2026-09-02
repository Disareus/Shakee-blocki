package com.shakeeblocki.animation;

import com.shakeeblocki.config.ShakeeConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Manages active block animation lifecycles, visibility suppression, and multi-block structure synchronizations.
 */
public final class ShakeeAnimationManager {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int PENDING_LIFETIME_TICKS = 20;
    private static final int MAX_ACTIVE_ANIMATIONS = 24;

    // Animation maps & primitive tracking collections
    private static final Long2ObjectOpenHashMap<ShakeeAnimationState> ACTIVE = new Long2ObjectOpenHashMap<>();
    private static final LongSet INVISIBLE = LongSets.synchronize(new LongOpenHashSet());
    private static final Long2ObjectOpenHashMap<PendingPlacement> PENDING = new Long2ObjectOpenHashMap<>();
    private static final LongArrayList TO_REMOVE = new LongArrayList();
    private static final LongSet DIRTY_SECTIONS = new LongOpenHashSet();

    private static long clientTicks = 0L;
    private static long lastPlacementTick = -100L;

    private ShakeeAnimationManager() {}

    /**
     * Ticks animation manager on the client main thread. Handles decay, destruction, and handoff transitions.
     */
    public static void tick() {
        clientTicks++;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            clear();
            return;
        }

        PENDING.values().removeIf(pending -> pending.expiresAt() <= clientTicks);
        TO_REMOVE.clear();

        for (Long2ObjectMap.Entry<ShakeeAnimationState> entry : ACTIVE.long2ObjectEntrySet()) {
            long posLong = entry.getLongKey();
            ShakeeAnimationState state = entry.getValue();
            BlockPos pos = state.pos();

            BlockState current = world.getBlockState(pos);
            if (!current.is(state.originalState().getBlock())) {
                if (state.kind() != AnimationKind.BREAK || (!state.isBreakingReleasing() && !current.isAir())) {
                    TO_REMOVE.add(posLong);
                    continue;
                }
            }

            if (state.kind() == AnimationKind.BREAK) {
                if (!current.isAir()) {
                    state.updateState(current);
                }

                if (state.isBreakingExpired(clientTicks) || current.isAir() || state.isDestroyed()) {
                    if (!state.isBreakingReleasing()) {
                        if (current.isAir() || state.isDestroyed()) {
                            state.markDestroyed(clientTicks);
                        } else {
                            state.beginBreakingRelease(clientTicks);
                        }
                    } else if (state.isBreakingReleaseFinished(clientTicks)) {
                        if (state.isDestroyed()) {
                            TO_REMOVE.add(posLong);
                        } else if (state.usesCustomWorldRender()) {
                            if (!state.isInHandoff()) {
                                unhideStructure(world, pos, state.originalState());
                                state.beginHandoff(clientTicks);
                            } else if (state.isHandoffFinished(clientTicks)) {
                                TO_REMOVE.add(posLong);
                            }
                        } else {
                            TO_REMOVE.add(posLong);
                        }
                    }
                }
                continue;
            }

            if (state.isAnimationFinished(clientTicks)) {
                if (state.usesCustomWorldRender()) {
                    if (!state.isInHandoff()) {
                        unhideStructure(world, pos, state.originalState());
                        state.beginHandoff(clientTicks);
                    } else if (state.isHandoffFinished(clientTicks)) {
                        TO_REMOVE.add(posLong);
                    }
                } else {
                    TO_REMOVE.add(posLong);
                }
            }
        }

        for (int i = 0; i < TO_REMOVE.size(); i++) {
            long posLong = TO_REMOVE.getLong(i);
            ShakeeAnimationState removed = ACTIVE.remove(posLong);
            if (removed != null && removed.usesCustomWorldRender()) {
                unhideStructure(world, removed.pos(), removed.originalState());
            }
        }

        DIRTY_SECTIONS.clear();
    }

    public static boolean hasActiveAnimations() {
        return !ACTIVE.isEmpty();
    }

    public static Collection<ShakeeAnimationState> activeAnimations() {
        return ACTIVE.values();
    }

    public static boolean isInvisible(BlockPos pos) {
        return pos != null && INVISIBLE.contains(pos.asLong());
    }

    public static boolean isInvisible(long posLong) {
        return INVISIBLE.contains(posLong);
    }

    public static boolean isAnimatedOrInvisible(BlockPos pos) {
        return isInvisible(pos);
    }

    public static ShakeeAnimationState get(BlockPos pos) {
        return ACTIVE.get(pos.asLong());
    }

    public static ShakeeAnimationState getBreaking(BlockPos pos) {
        ShakeeAnimationState state = ACTIVE.get(pos.asLong());
        return state != null && state.kind() == AnimationKind.BREAK ? state : null;
    }

    public static long getClientTicks() {
        return clientTicks;
    }

    public static void onBreakingProgress(ClientLevel world, BlockPos pos, BlockState state, Direction face, float progress) {
        if (!ShakeeConfig.get().enableBreakingAnimation || state.isAir() || !isBlockAllowed(state)) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int horizontalSign = random.nextBoolean() ? 1 : -1;
        int verticalSign = random.nextBoolean() ? 1 : -1;

        startOrRefreshBreaking(world, pos.immutable(), state, face, horizontalSign, verticalSign, progress);

        if (state.getBlock() instanceof ChestBlock) {
            BlockPos otherPos = getChestOtherPos(pos, state);
            if (otherPos != null) {
                BlockState otherState = world.getBlockState(otherPos);
                if (isValidChestPair(state, otherState)) {
                    startOrRefreshBreaking(world, otherPos.immutable(), otherState, face, horizontalSign, verticalSign, progress);
                }
            }
            return;
        }

        if (ShakeeConfig.get().breakingAnimateConnectedBlocks) {
            BlockPos otherPos = getConnectedPos(pos, state);
            if (otherPos != null) {
                BlockState otherState = world.getBlockState(otherPos);
                if (!otherState.isAir() && otherState.is(state.getBlock())) {
                    startOrRefreshBreaking(world, otherPos.immutable(), otherState, face, horizontalSign, verticalSign, progress);
                }
            }
        }
    }

    private static void startOrRefreshBreaking(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            Direction face,
            int horizontalSign,
            int verticalSign,
            float progress
    ) {
        long posLong = pos.asLong();
        ShakeeAnimationState existing = ACTIVE.get(posLong);

        if (existing != null && existing.kind() == AnimationKind.BREAK && state.is(existing.originalState().getBlock())) {
            existing.updateState(state);
            existing.updateBreakProgress(progress);
            existing.refresh(clientTicks);
            return;
        }

        ShakeeAnimationState created = new ShakeeAnimationState(
                pos,
                state,
                face,
                clientTicks,
                ShakeeConfig.get().breakingLoopTicks,
                horizontalSign,
                verticalSign,
                AnimationKind.BREAK
        );
        created.updateBreakProgress(progress);
        ACTIVE.put(posLong, created);
        created.refresh(clientTicks);

        if (shouldUseCustomWorldRender(state)) {
            hideStructure(world, pos, state);
        }
    }

    public static void stopBreaking(ClientLevel world, BlockPos pos) {
        ShakeeAnimationState state = ACTIVE.get(pos.asLong());
        if (state != null && state.kind() == AnimationKind.BREAK) {
            state.beginBreakingRelease(clientTicks);
        }

        BlockState blockState = world.getBlockState(pos);
        if (blockState.getBlock() instanceof ChestBlock) {
            BlockPos otherPos = getChestOtherPos(pos, blockState);
            if (otherPos != null) {
                BlockState otherState = world.getBlockState(otherPos);
                if (isValidChestPair(blockState, otherState)) {
                    ShakeeAnimationState other = ACTIVE.get(otherPos.asLong());
                    if (other != null && other.kind() == AnimationKind.BREAK) {
                        other.beginBreakingRelease(clientTicks);
                    }
                }
            }
            return;
        }

        BlockPos otherPos = getConnectedPos(pos, blockState);
        if (otherPos != null) {
            ShakeeAnimationState other = ACTIVE.get(otherPos.asLong());
            if (other != null && other.kind() == AnimationKind.BREAK) {
                other.beginBreakingRelease(clientTicks);
            }
        }
    }

    public static void cancelBreakingNow(ClientLevel world, BlockPos pos) {
        removeBreakingStateNow(world, pos);

        BlockState blockState = world.getBlockState(pos);
        if (blockState.getBlock() instanceof ChestBlock) {
            BlockPos otherPos = getChestOtherPos(pos, blockState);
            if (otherPos != null) {
                removeBreakingStateNow(world, otherPos);
            }
            return;
        }

        BlockPos otherPos = getConnectedPos(pos, blockState);
        if (otherPos != null) {
            removeBreakingStateNow(world, otherPos);
        }
    }

    public static void clear() {
        ACTIVE.clear();
        INVISIBLE.clear();
        PENDING.clear();
        TO_REMOVE.clear();
        DIRTY_SECTIONS.clear();
    }

    public static void markPlacementAttempt(BlockPos targetPos, Block block, Direction face, Vec3 velocity) {
        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enablePlacementAnimation) return;
        if (block == null || !config.isBlockAllowed(block)) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int horizontalSign = random.nextBoolean() ? 1 : -1;
        int verticalSign = random.nextBoolean() ? 1 : -1;

        PendingPlacement pending = new PendingPlacement(
                targetPos.immutable(),
                block,
                face,
                horizontalSign,
                verticalSign,
                velocity,
                clientTicks + PENDING_LIFETIME_TICKS
        );

        PENDING.put(targetPos.asLong(), pending);
    }

    public static void cancelPlacementAttempt(BlockPos targetPos) {
        PENDING.remove(targetPos.asLong());
    }

    public static void onInstantBreak(ClientLevel world, BlockPos pos, BlockState state, Direction face) {
        if (!ShakeeConfig.get().enableBreakingAnimation || state == null || state.isAir() || !isBlockAllowed(state)) return;

        BlockPos immutablePos = pos.immutable();
        long posLong = immutablePos.asLong();

        ShakeeAnimationState existing = ACTIVE.get(posLong);
        if (existing != null && existing.kind() == AnimationKind.BREAK) {
            existing.markDestroyed(clientTicks);
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int horizontalSign = random.nextBoolean() ? 1 : -1;
        int verticalSign = random.nextBoolean() ? 1 : -1;

        onInstantBreakSingle(world, immutablePos, state, face, horizontalSign, verticalSign);

        if (state.getBlock() instanceof ChestBlock) {
            BlockPos otherPos = getChestOtherPos(immutablePos, state);
            if (otherPos != null) {
                BlockState otherState = world.getBlockState(otherPos);
                if (isValidChestPair(state, otherState)) {
                    onInstantBreakSingle(world, otherPos, otherState, face, horizontalSign, verticalSign);
                }
            }
        } else if (ShakeeConfig.get().breakingAnimateConnectedBlocks) {
            BlockPos otherPos = getConnectedPos(immutablePos, state);
            if (otherPos != null) {
                BlockState otherState = world.getBlockState(otherPos);
                if (!otherState.isAir() && otherState.is(state.getBlock())) {
                    onInstantBreakSingle(world, otherPos, otherState, face, horizontalSign, verticalSign);
                }
            }
        }
    }

    private static void onInstantBreakSingle(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            Direction face,
            int horizontalSign,
            int verticalSign
    ) {
        ShakeeAnimationState created = new ShakeeAnimationState(
                pos,
                state,
                face != null ? face : Direction.UP,
                clientTicks,
                Math.max(3, ShakeeConfig.get().breakingLoopTicks),
                horizontalSign,
                verticalSign,
                AnimationKind.BREAK
        );
        created.markDestroyed(clientTicks);
        ACTIVE.put(pos.asLong(), created);
    }

    public static void onClientBlockUpdated(ClientLevel world, BlockPos pos, BlockState state) {
        BlockPos immutablePos = pos.immutable();
        long posLong = immutablePos.asLong();

        // When block is destroyed (becomes Air)
        if (state.isAir()) {
            ShakeeAnimationState existing = ACTIVE.get(posLong);
            if (existing != null && existing.kind() == AnimationKind.BREAK) {
                existing.markDestroyed(clientTicks);
            }
            return;
        }

        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enablePlacementAnimation || !isBlockAllowed(state)) {
            PENDING.remove(posLong);
            return;
        }

        if (state.getBlock() instanceof ChestBlock) {
            refreshExisting(world, immutablePos, state);

            BlockPos otherPos = getChestOtherPos(immutablePos, state);
            BlockState otherState = otherPos != null ? world.getBlockState(otherPos) : null;

            if (isValidChestPair(state, otherState)) {
                refreshExisting(world, otherPos, otherState);
            }

            PendingPlacement pendingHere = PENDING.get(posLong);
            if (pendingHere != null && state.is(pendingHere.block())) {
                startOrRefreshChest(world, immutablePos, state, pendingHere.face(), pendingHere.horizontalSign(), pendingHere.verticalSign(), pendingHere.velocity());
                PENDING.remove(posLong);
                if (otherPos != null) PENDING.remove(otherPos.asLong());
                return;
            }

            if (isValidChestPair(state, otherState)) {
                PendingPlacement pendingOther = PENDING.get(otherPos.asLong());
                if (pendingOther != null && otherState.is(pendingOther.block())) {
                    startOrRefreshChest(world, immutablePos, state, pendingOther.face(), pendingOther.horizontalSign(), pendingOther.verticalSign(), pendingOther.velocity());
                    PENDING.remove(otherPos.asLong());
                    PENDING.remove(posLong);
                    return;
                }

                ShakeeAnimationState otherAnimation = ACTIVE.get(otherPos.asLong());
                if (otherAnimation != null && otherAnimation.originalState().is(state.getBlock())) {
                    if (ACTIVE.get(posLong) == null) {
                        startSyncedFrom(world, immutablePos, state, otherAnimation);
                    } else {
                        refreshExisting(world, immutablePos, state);
                    }
                }
            }
            return;
        }

        refreshExisting(world, immutablePos, state);

        BlockPos otherPos = getConnectedPos(immutablePos, state);
        if (otherPos != null) {
            BlockState otherState = world.getBlockState(otherPos);
            refreshExisting(world, otherPos, otherState);
        }

        PendingPlacement pending = PENDING.get(posLong);
        if (pending != null && state.is(pending.block())) {
            if (ACTIVE.get(posLong) == null) {
                start(world, immutablePos, state, pending.face(), pending.horizontalSign(), pending.verticalSign(), pending.velocity());
            } else {
                refreshExisting(world, immutablePos, state);
            }

            startConnectedIfPresent(world, immutablePos, state, pending.face(), pending.horizontalSign(), pending.verticalSign(), pending.velocity());
            PENDING.remove(posLong);
            return;
        }

        ShakeeAnimationState connectedAnimation = findConnectedAnimation(world, immutablePos, state);
        if (connectedAnimation != null) {
            if (ACTIVE.get(posLong) == null) {
                startSyncedFrom(world, immutablePos, state, connectedAnimation);
            } else {
                refreshExisting(world, immutablePos, state);
            }
        }
    }

    public static void start(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            Direction face,
            int horizontalSign,
            int verticalSign,
            Vec3 velocity
    ) {
        if (state.isAir() || !isBlockAllowed(state) || !ShakeeConfig.get().enablePlacementAnimation) return;

        enforceMaxActiveAnimations(world);

        BlockPos immutablePos = pos.immutable();
        ACTIVE.put(
                immutablePos.asLong(),
                new ShakeeAnimationState(
                        immutablePos,
                        state,
                        face,
                        clientTicks,
                        ShakeeConfig.get().durationTicks,
                        horizontalSign,
                        verticalSign,
                        AnimationKind.PLACE,
                        velocity
                )
        );

        if (shouldUseCustomWorldRender(state)) {
            hideStructure(world, immutablePos, state);
        }

        // Fast-Build Throttle: if placing rapidly (<= 3 ticks apart),
        // throttle neighbor ripples to reduce chunk meshing overhead by ~85%!
        boolean isFastBuilding = (clientTicks - lastPlacementTick) <= 3;
        lastPlacementTick = clientTicks;

        if (ShakeeConfig.get().enableNeighborRipple && !isFastBuilding) {
            triggerNeighborRipples(world, immutablePos);
        }
    }

    private static void enforceMaxActiveAnimations(ClientLevel world) {
        if (ACTIVE.size() < MAX_ACTIVE_ANIMATIONS) return;

        long oldestKey = -1L;
        long oldestTick = Long.MAX_VALUE;
        ShakeeAnimationState oldestState = null;

        for (Long2ObjectMap.Entry<ShakeeAnimationState> entry : ACTIVE.long2ObjectEntrySet()) {
            ShakeeAnimationState s = entry.getValue();
            if (s.kind() != AnimationKind.BREAK && s.startTick() < oldestTick) {
                oldestTick = s.startTick();
                oldestKey = entry.getLongKey();
                oldestState = s;
            }
        }

        if (oldestState != null) {
            ACTIVE.remove(oldestKey);
            if (oldestState.usesCustomWorldRender()) {
                unhideStructure(world, oldestState.pos(), oldestState.originalState());
            }
        }
    }

    private static void triggerNeighborRipples(ClientLevel world, BlockPos centerPos) {
        for (Direction dir : DIRECTIONS) {
            BlockPos neighborPos = centerPos.relative(dir);
            long neighborLong = neighborPos.asLong();
            if (ACTIVE.containsKey(neighborLong) || INVISIBLE.contains(neighborLong)) continue;

            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.isAir() || !isBlockAllowed(neighborState) || !shouldUseCustomWorldRender(neighborState)) continue;

            ACTIVE.put(
                    neighborLong,
                    new ShakeeAnimationState(
                            neighborPos.immutable(),
                            neighborState,
                            Direction.UP,
                            clientTicks,
                            5,
                            1,
                            1,
                            AnimationKind.RIPPLE
                    )
            );

            if (INVISIBLE.add(neighborLong)) {
                rerender(world, neighborPos);
            }
        }
    }

    private static void startConnectedIfPresent(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            Direction face,
            int horizontalSign,
            int verticalSign,
            Vec3 velocity
    ) {
        if (state.getBlock() instanceof ChestBlock) return;

        BlockPos otherPos = getConnectedPos(pos, state);
        if (otherPos == null) return;

        BlockState otherState = world.getBlockState(otherPos);
        if (otherState.isAir() || !otherState.is(state.getBlock())) return;

        ShakeeAnimationState existing = ACTIVE.get(otherPos.asLong());
        if (existing != null) {
            existing.updateState(otherState);
            return;
        }

        start(world, otherPos, otherState, face, horizontalSign, verticalSign, velocity);
    }

    private static ShakeeAnimationState findConnectedAnimation(ClientLevel world, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof ChestBlock) return null;

        BlockPos otherPos = getConnectedPos(pos, state);
        if (otherPos == null) return null;

        ShakeeAnimationState other = ACTIVE.get(otherPos.asLong());
        if (other == null || !other.originalState().is(state.getBlock())) return null;

        return other;
    }

    private static BlockPos getConnectedPos(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            Direction otherHalf = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
            return pos.relative(otherHalf);
        }

        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BedBlock.FACING);
            return (state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite())).immutable();
        }

        return null;
    }

    public static void rerender(ClientLevel world, BlockPos pos) {
        if (world == null || pos == null) return;
        long sectionKey = SectionPos.asLong(pos);
        if (DIRTY_SECTIONS.add(sectionKey)) {
            BlockState current = world.getBlockState(pos);
            world.sendBlockUpdated(pos, current, current, Block.UPDATE_CLIENTS);
        }
    }

    private static void refreshExisting(ClientLevel world, BlockPos pos, BlockState state) {
        ShakeeAnimationState existing = ACTIVE.get(pos.asLong());
        if (existing != null && !state.isAir() && state.is(existing.originalState().getBlock())) {
            existing.updateState(state);
        }
    }

    private static void removeBreakingStateNow(ClientLevel world, BlockPos pos) {
        long posLong = pos.asLong();
        ShakeeAnimationState state = ACTIVE.get(posLong);
        if (state == null || state.kind() != AnimationKind.BREAK || state.isDestroyed()) return;

        ACTIVE.remove(posLong);
        if (state.usesCustomWorldRender()) {
            unhideStructure(world, pos, state.originalState());
        }
    }

    public static void startSyncedFrom(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            ShakeeAnimationState source
    ) {
        if (state.isAir()) return;

        BlockPos immutablePos = pos.immutable();
        ACTIVE.put(
                immutablePos.asLong(),
                new ShakeeAnimationState(
                        immutablePos,
                        state,
                        source.face(),
                        source.startTick(),
                        ShakeeConfig.get().durationTicks,
                        source.horizontalSign(),
                        source.verticalSign(),
                        source.kind(),
                        source.velocity()
                )
        );
    }

    public static ShakeeAnimationState getForRender(ClientLevel world, BlockPos pos) {
        long posLong = pos.asLong();
        ShakeeAnimationState direct = ACTIVE.get(posLong);
        if (direct != null) return direct;

        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return null;

        BlockPos otherPos = getConnectedPos(pos, state);
        if (otherPos == null) return null;

        ShakeeAnimationState other = ACTIVE.get(otherPos.asLong());
        if (other == null || !other.originalState().is(state.getBlock())) return null;

        startSyncedFrom(world, pos, state, other);
        return ACTIVE.get(posLong);
    }

    public static ShakeeAnimationState getVisualAnimation(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return null;

        if (state.getBlock() instanceof ChestBlock) {
            return getForChestRender(world, pos);
        }

        return getForRender(world, pos);
    }

    public static ShakeeAnimationState getForChestRender(ClientLevel world, BlockPos pos) {
        long posLong = pos.asLong();
        ShakeeAnimationState direct = ACTIVE.get(posLong);
        if (direct != null) return direct;

        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !(state.getBlock() instanceof ChestBlock)) return null;

        BlockPos otherPos = getChestOtherPos(pos, state);
        if (otherPos == null) return null;

        BlockState otherState = world.getBlockState(otherPos);
        if (!isValidChestPair(state, otherState)) return null;

        ShakeeAnimationState other = ACTIVE.get(otherPos.asLong());
        if (other == null || !other.originalState().is(state.getBlock())) return null;

        return new ShakeeAnimationState(
                pos.immutable(),
                state,
                other.face(),
                other.startTick(),
                other.kind() == AnimationKind.BREAK
                        ? ShakeeConfig.get().breakingLoopTicks
                        : ShakeeConfig.get().durationTicks,
                other.horizontalSign(),
                other.verticalSign(),
                other.kind(),
                other.velocity()
        );
    }

    private static void hideStructure(ClientLevel world, BlockPos pos, BlockState state) {
        forEachStructurePos(world, pos, state, renderPos -> {
            if (INVISIBLE.add(renderPos.asLong())) {
                rerender(world, renderPos);
            }
        });
    }

    private static void unhideStructure(ClientLevel world, BlockPos pos, BlockState state) {
        forEachStructurePos(world, pos, state, renderPos -> {
            if (INVISIBLE.remove(renderPos.asLong())) {
                rerender(world, renderPos);
            }
        });
    }

    /**
     * Traverses connected positions of a multi-block structure without allocating collections.
     */
    private static void forEachStructurePos(ClientLevel world, BlockPos pos, BlockState state, Consumer<BlockPos> consumer) {
        consumer.accept(pos);

        if (state.getBlock() instanceof ChestBlock) {
            BlockPos otherPos = getChestOtherPos(pos, state);
            if (otherPos != null) {
                BlockState otherState = world.getBlockState(otherPos);
                if (isValidChestPair(state, otherState)) {
                    consumer.accept(otherPos);
                }
            }
            return;
        }

        BlockPos other = getConnectedPos(pos, state);
        if (other != null) {
            consumer.accept(other);
        }
    }

    private static boolean isChest(BlockState state) {
        return state != null && state.getBlock() instanceof ChestBlock;
    }

    public static BlockPos getChestOtherPos(BlockPos pos, BlockState state) {
        if (!isChest(state)) return null;

        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return null;

        Direction facing = state.getValue(ChestBlock.FACING);
        Direction partnerDir = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        return pos.relative(partnerDir);
    }

    public static Direction getChestPartnerDirection(BlockPos pos, BlockState state) {
        if (!isChest(state)) return null;

        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return null;

        Direction facing = state.getValue(ChestBlock.FACING);
        return type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private static boolean shouldUseCustomWorldRender(BlockState state) {
        return state.getRenderShape() == RenderShape.MODEL
                && !(state.getBlock() instanceof ChestBlock)
                && !(state.getBlock() instanceof SignBlock);
    }

    private static boolean isValidChestPair(BlockState state, BlockState otherState) {
        if (!isChest(state) || !isChest(otherState)) return false;

        ChestType type = state.getValue(ChestBlock.TYPE);
        ChestType otherType = otherState.getValue(ChestBlock.TYPE);

        if (type == ChestType.SINGLE || otherType == ChestType.SINGLE) return false;
        if (!state.is(otherState.getBlock())) return false;
        if (state.getValue(ChestBlock.FACING) != otherState.getValue(ChestBlock.FACING)) return false;
        return type != otherType;
    }

    private static void startOrRefreshChest(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            Direction face,
            int horizontalSign,
            int verticalSign,
            Vec3 velocity
    ) {
        BlockPos otherPos = getChestOtherPos(pos, state);
        BlockState otherState = otherPos != null ? world.getBlockState(otherPos) : null;

        if (isValidChestPair(state, otherState)) {
            startOrRefreshChestHalf(world, pos, state, face, horizontalSign, verticalSign, velocity);
            startOrRefreshChestHalf(world, otherPos, otherState, face, horizontalSign, verticalSign, velocity);
            return;
        }

        startOrRefreshChestHalf(world, pos, state, face, horizontalSign, verticalSign, velocity);
    }

    private static void startOrRefreshChestHalf(
            ClientLevel world,
            BlockPos pos,
            BlockState state,
            Direction face,
            int horizontalSign,
            int verticalSign,
            Vec3 velocity
    ) {
        ShakeeAnimationState existing = ACTIVE.get(pos.asLong());
        if (existing == null) {
            start(world, pos, state, face, horizontalSign, verticalSign, velocity);
        } else {
            existing.updateState(state);
        }
    }

    public static boolean isBlockAllowed(BlockState state) {
        if (state == null || state.isAir()) return false;
        return ShakeeConfig.get().isBlockAllowed(state.getBlock());
    }

    private record PendingPlacement(
            BlockPos targetPos,
            Block block,
            Direction face,
            int horizontalSign,
            int verticalSign,
            Vec3 velocity,
            long expiresAt
    ) {}
}
