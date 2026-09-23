package com.shakeeblocki.animation;

import com.shakeeblocki.config.ShakeeConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
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
    private static final Long2ObjectOpenHashMap<PendingPlacement> PENDING = new Long2ObjectOpenHashMap<>();
    private static final LongArrayList TO_REMOVE = new LongArrayList();
    private static final LongSet DIRTY_SECTIONS = new LongOpenHashSet();

    private static long clientTicks = 0L;
    private static long lastPlacementTick = -100L;
    private static volatile boolean activeAnimationsPresent;
    private static ClientLevel trackedWorld;

    private ShakeeAnimationManager() {}

    /**
     * Ticks animation manager on the client main thread. Handles decay, destruction, and handoff transitions.
     */
    public static void tick() {
        clientTicks++;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world != trackedWorld) {
            clear();
            trackedWorld = world;
        }
        if (world == null) {
            return;
        }

        ShakeeConfig config = ShakeeConfig.get();

        if (!PENDING.isEmpty()) {
            PENDING.values().removeIf(pending -> pending.expiresAt() <= clientTicks);
        }
        TO_REMOVE.clear();

        for (Long2ObjectMap.Entry<ShakeeAnimationState> entry : ACTIVE.long2ObjectEntrySet()) {
            long posLong = entry.getLongKey();
            ShakeeAnimationState state = entry.getValue();
            BlockPos pos = state.pos();

            if ((state.kind() == AnimationKind.BREAK && !config.enableBreakingAnimation)
                    || (state.kind() != AnimationKind.BREAK && !config.enablePlacementAnimation)) {
                TO_REMOVE.add(posLong);
                continue;
            }

            BlockState current = world.getBlockState(pos);
            boolean destroyedBreakWithAir = state.kind() == AnimationKind.BREAK
                    && state.isDestroyed()
                    && current.isAir();
            if (!destroyedBreakWithAir && !current.is(state.originalState().getBlock())) {
                TO_REMOVE.add(posLong);
                continue;
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
                    } else if (state.isSettlingFinished(clientTicks)) {
                        if (state.isDestroyed()) {
                            TO_REMOVE.add(posLong);
                        } else if (state.usesCustomWorldRender()) {
                            if (state.phase() != AnimationPhase.RESTORING) {
                                unhideStructure(world, pos, state.originalState());
                                state.enterRestoringPhase(clientTicks);
                            } else if (state.isRestoringFinished(clientTicks)) {
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
                    if (state.phase() != AnimationPhase.RESTORING) {
                        unhideStructure(world, pos, state.originalState());
                        state.enterRestoringPhase(clientTicks);
                    } else if (state.isRestoringFinished(clientTicks)) {
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
        activeAnimationsPresent = !ACTIVE.isEmpty();

        DIRTY_SECTIONS.clear();
    }

    public static boolean hasActiveAnimations() {
        return activeAnimationsPresent;
    }

    public static Collection<ShakeeAnimationState> activeAnimations() {
        return ACTIVE.values();
    }

    public static boolean isInvisible(BlockPos pos) {
        return pos != null && ShakeeRenderSuppressor.isSuppressed(pos.asLong());
    }

    public static boolean isInvisible(long posLong) {
        return ShakeeRenderSuppressor.isSuppressed(posLong);
    }

    public static boolean isAnimatedOrInvisible(BlockPos pos) {
        return pos != null && isAnimatedOrInvisible(pos.asLong());
    }

    public static boolean isAnimatedOrInvisible(long posLong) {
        return ACTIVE.containsKey(posLong) || ShakeeRenderSuppressor.isSuppressed(posLong);
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
            boolean wasRestoring = existing.phase() == AnimationPhase.RESTORING;
            existing.updateState(state);
            existing.updateBreakProgress(progress);
            existing.refresh(clientTicks);
            if (wasRestoring && existing.usesCustomWorldRender()) {
                hideStructure(world, pos, state);
            }
            return;
        }

        if (existing != null) {
            removeStateNow(world, pos);
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
        activeAnimationsPresent = true;
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
        activeAnimationsPresent = false;
        PENDING.clear();
        TO_REMOVE.clear();
        DIRTY_SECTIONS.clear();
        lastPlacementTick = -100L;
        trackedWorld = null;
        ShakeeRenderSuppressor.clear();
    }

    public static void markPlacementAttempt(BlockPos targetPos, Block block, Direction face, Vec3 velocity) {
        ShakeeConfig config = ShakeeConfig.get();
        if (!config.enablePlacementAnimation) return;
        if (block == null || !config.isBlockAllowed(block)) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int horizontalSign = random.nextBoolean() ? 1 : -1;
        int verticalSign = random.nextBoolean() ? 1 : -1;

        PendingPlacement pending = new PendingPlacement(
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
        if (ACTIVE.containsKey(pos.asLong())) {
            removeStateNow(world, pos);
        }

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
        activeAnimationsPresent = true;
    }

    public static void onClientBlockUpdated(ClientLevel world, BlockPos pos, BlockState state) {
        long posLong = pos.asLong();

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
            refreshExisting(pos, state);

            BlockPos otherPos = getChestOtherPos(pos, state);
            BlockState otherState = otherPos != null ? world.getBlockState(otherPos) : null;

            if (isValidChestPair(state, otherState)) {
                refreshExisting(otherPos, otherState);
            }

            PendingPlacement pendingHere = PENDING.get(posLong);
            if (pendingHere != null) {
                boolean placedHere = state.is(pendingHere.block());
                if (placedHere) {
                    startOrRefreshChest(world, pos, state, pendingHere.face(), pendingHere.horizontalSign(), pendingHere.verticalSign(), pendingHere.velocity());
                    if (otherPos != null) PENDING.remove(otherPos.asLong());
                }
                PENDING.remove(posLong);
                if (placedHere) return;
            }

            if (isValidChestPair(state, otherState)) {
                PendingPlacement pendingOther = PENDING.get(otherPos.asLong());
                if (pendingOther != null) {
                    if (otherState.is(pendingOther.block())) {
                        startOrRefreshChest(world, pos, state, pendingOther.face(), pendingOther.horizontalSign(), pendingOther.verticalSign(), pendingOther.velocity());
                    }
                    PENDING.remove(otherPos.asLong());
                    PENDING.remove(posLong);
                    if (otherState.is(pendingOther.block())) return;
                }

                ShakeeAnimationState otherAnimation = ACTIVE.get(otherPos.asLong());
                if (otherAnimation != null && otherAnimation.originalState().is(state.getBlock())) {
                    if (ACTIVE.get(posLong) == null) {
                        startSyncedFrom(world, pos, state, otherAnimation);
                    } else {
                        refreshExisting(pos, state);
                    }
                }
            }
            return;
        }

        refreshExisting(pos, state);

        BlockPos otherPos = getConnectedPos(pos, state);
        if (otherPos != null) {
            BlockState otherState = world.getBlockState(otherPos);
            refreshExisting(otherPos, otherState);
        }

        PendingPlacement pending = PENDING.get(posLong);
        if (pending != null) {
            if (state.is(pending.block())) {
                ShakeeAnimationState existing = ACTIVE.get(posLong);
                if (existing == null || existing.kind() != AnimationKind.PLACE
                        || !state.is(existing.originalState().getBlock())) {
                    if (existing != null) removeStateNow(world, pos);
                    start(world, pos, state, pending.face(), pending.horizontalSign(), pending.verticalSign(), pending.velocity());
                } else {
                    refreshExisting(pos, state);
                }

                startConnectedIfPresent(world, pos, state, pending.face(), pending.horizontalSign(), pending.verticalSign(), pending.velocity());
                PENDING.remove(posLong);
                return;
            }
            PENDING.remove(posLong);
        }

        ShakeeAnimationState connectedAnimation = findConnectedAnimation(pos, state);
        if (connectedAnimation != null) {
            if (ACTIVE.get(posLong) == null) {
                startSyncedFrom(world, pos, state, connectedAnimation);
            } else {
                refreshExisting(pos, state);
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

        if (!enforceMaxActiveAnimations(world)) return;

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
        activeAnimationsPresent = true;

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

    private static boolean enforceMaxActiveAnimations(ClientLevel world) {
        if (ACTIVE.size() < MAX_ACTIVE_ANIMATIONS) return true;

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
            activeAnimationsPresent = !ACTIVE.isEmpty();
            return true;
        }
        return false;
    }

    private static void triggerNeighborRipples(ClientLevel world, BlockPos centerPos) {
        for (Direction dir : DIRECTIONS) {
            if (ACTIVE.size() >= MAX_ACTIVE_ANIMATIONS) break;
            BlockPos neighborPos = centerPos.relative(dir);
            long neighborLong = neighborPos.asLong();
            if (ACTIVE.containsKey(neighborLong) || ShakeeRenderSuppressor.isSuppressed(neighborLong)) continue;

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
            activeAnimationsPresent = true;

            if (ShakeeRenderSuppressor.suppress(neighborLong)) {
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
            if (existing.kind() == AnimationKind.PLACE && otherState.is(existing.originalState().getBlock())) {
                existing.updateState(otherState);
            } else {
                removeStateNow(world, otherPos);
                start(world, otherPos, otherState, face, horizontalSign, verticalSign, velocity);
            }
            return;
        }

        start(world, otherPos, otherState, face, horizontalSign, verticalSign, velocity);
    }

    private static ShakeeAnimationState findConnectedAnimation(BlockPos pos, BlockState state) {
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

    private static void refreshExisting(BlockPos pos, BlockState state) {
        ShakeeAnimationState existing = ACTIVE.get(pos.asLong());
        if (existing != null && !state.isAir() && state.is(existing.originalState().getBlock())) {
            existing.updateState(state);
        }
    }

    private static void removeStateNow(ClientLevel world, BlockPos pos) {
        ShakeeAnimationState state = ACTIVE.remove(pos.asLong());
        if (state != null && state.usesCustomWorldRender()) {
            unhideStructure(world, pos, state.originalState());
        }
        activeAnimationsPresent = !ACTIVE.isEmpty();
    }

    private static void removeBreakingStateNow(ClientLevel world, BlockPos pos) {
        long posLong = pos.asLong();
        ShakeeAnimationState state = ACTIVE.get(posLong);
        if (state == null || state.kind() != AnimationKind.BREAK || state.isDestroyed()) return;

        ACTIVE.remove(posLong);
        activeAnimationsPresent = !ACTIVE.isEmpty();
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
        ShakeeAnimationState synced = new ShakeeAnimationState(source, immutablePos, state);
        ACTIVE.put(immutablePos.asLong(), synced);
        activeAnimationsPresent = true;
        if (synced.usesCustomWorldRender()) {
            hideStructure(world, immutablePos, state);
        }
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

        startSyncedFrom(world, pos, state, other);
        return ACTIVE.get(posLong);
    }

    private static void hideStructure(ClientLevel world, BlockPos pos, BlockState state) {
        forEachStructurePos(world, pos, state, renderPos -> {
            if (ShakeeRenderSuppressor.suppress(renderPos.asLong())) {
                rerender(world, renderPos);
            }
        });
    }

    private static void unhideStructure(ClientLevel world, BlockPos pos, BlockState state) {
        forEachStructurePos(world, pos, state, renderPos -> {
            long pLong = renderPos.asLong();
            if (ShakeeRenderSuppressor.release(pLong)) {
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
        return ShakeeAnimationState.shouldUseCustomWorldRender(state);
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
        if (existing == null || existing.kind() != AnimationKind.PLACE
                || !state.is(existing.originalState().getBlock())) {
            if (existing != null) {
                removeStateNow(world, pos);
            }
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
            Block block,
            Direction face,
            int horizontalSign,
            int verticalSign,
            Vec3 velocity,
            long expiresAt
    ) {}
}
