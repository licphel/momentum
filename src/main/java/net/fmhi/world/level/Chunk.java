package net.fmhi.world.level;

import net.fmhi.collection.MappingArray;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.BlockStateHolder;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.Liquid;
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

  public void tick(double delta) {
    for (Entity e : List.copyOf(entities)) {
      if (e.lastTick == level.getTicks()) {
        continue;
      }
      e.lastTick = level.getTicks();
      e.tick(delta, level);

      ChunkPos newCp = e.position().toChunkPos();
      if (!newCp.equals(chunkPos)) {
        Chunk old = level.getChunk(chunkPos);
        if (old != null) old.removeEntity(e);
        level.getOrLoadChunk(newCp).addEntity(e);
        e.chunkPos = newCp;
      }
    }
  }

  public void setLoaded(boolean loaded) { isLoaded = loaded; }

  // -- liquids -------------------------------------------------------------

  /** The liquid level of a tile ({@code 0} = empty, {@code 255} = full). */
  public int getLiquidLevel(int wx, int wy) { return liquidMap.level(wx, wy); }

  /** The liquid id of a tile, see {@code Liquids#byId}. */
  public byte getLiquidType(int wx, int wy) { return liquidMap.liquidType(wx, wy); }

  /** Sets the liquid of a tile; levels {@code <= 0} clear it. */
  public void setLiquid(int wx, int wy, Liquid liquid, int level) {
    liquidMap.set(wx, wy, liquid, level);
  }

  /** Sets the liquid level of a tile; {@code <= 0} clears it. */
  public void setLiquidLevel(int wx, int wy, int level) {
    liquidMap.setLevel(wx, wy, level);
  }

  /** Sets the liquid type of a tile. */
  public void setLiquidType(int wx, int wy, byte id) {
    liquidMap.setType(wx, wy, id);
  }

  // -- blocks ---------------------------------------------------------------

  /** Whether the block mesh of this chunk must be rebuilt. */
  public boolean frontDirty;

  /** Whether the wall mesh of this chunk must be rebuilt. */
  public boolean backDirty;

  public void setBlock(int wx, int wy, BlockState state) {
    blockMap.set(wx, wy, state);
    frontDirty = true;
  }

  public void setBlock(BlockPos pos, BlockState state) { setBlock(pos.x(), pos.y(), state); }

  public BlockState getBlock(BlockPos pos) { return getBlock(pos.x(), pos.y()); }

  public BlockState getBlock(int wx, int wy) { return blockMap.get(wx, wy); }

  // -- walls ----------------------------------------------------------------

  public void setWall(int wx, int wy, BlockState state) {
    wallMap.set(wx, wy, state);
    backDirty = true;
  }

  public void setWall(BlockPos pos, BlockState state) { setWall(pos.x(), pos.y(), state); }

  public BlockState getWall(BlockPos pos) { return getWall(pos.x(), pos.y()); }

  public BlockState getWall(int wx, int wy) { return wallMap.get(wx, wy); }

  // -- entities -------------------------------------------------------------

  public void addEntity(Entity e) { entities.add(e); }

  public void removeEntity(Entity e) { entities.remove(e); }

  public List<Entity> entities() { return entities; }
}
