package net.fmhi.world.level;

import net.fmhi.collection.MappingArray;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.BlockStateHolder;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.LiquidMap;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Chunk {
  private final MappingArray<BlockState> blockMap =
      new MappingArray<>(BlockStateHolder.BLOCK_STATE_PROPERTY_PALETTE, ChunkPos.SIZE, 2);
  private final MappingArray<BlockState> wallMap =
      new MappingArray<>(BlockStateHolder.BLOCK_STATE_PROPERTY_PALETTE, ChunkPos.SIZE, 2);

  private final List<Entity> entities = new ArrayList<>();
  private final LiquidMap liquidMap = new LiquidMap();

  public final Level level;
  public final ChunkPos chunkPos;
  public boolean isLoaded;

  public Chunk(Level level, ChunkPos chunkPos) {
    this.level = level;
    this.chunkPos = chunkPos;
  }

  public void tick(double delta) {}

  public void setLoaded(boolean loaded) { isLoaded = loaded; }

  public LiquidMap liquidMap() { return liquidMap; }

  // -- blocks ---------------------------------------------------------------

  public void setBlock(BlockPos pos, BlockState state) { blockMap.set(pos.x(), pos.y(), state); }

  public BlockState getBlock(BlockPos pos) { return blockMap.get(pos.x(), pos.y()); }

  public BlockState getBlock(int lx, int ly) { return blockMap.get(lx, ly); }

  // -- walls ----------------------------------------------------------------

  public void setWall(BlockPos pos, BlockState state) { wallMap.set(pos.x(), pos.y(), state); }

  public BlockState getWall(BlockPos pos) { return wallMap.get(pos.x(), pos.y()); }

  public BlockState getWall(int lx, int ly) { return wallMap.get(lx, ly); }

  // -- entities -------------------------------------------------------------

  public void addEntity(Entity e) { entities.add(e); }

  public void removeEntity(Entity e) { entities.remove(e); }

  public List<Entity> entities() { return Collections.unmodifiableList(entities); }
}
