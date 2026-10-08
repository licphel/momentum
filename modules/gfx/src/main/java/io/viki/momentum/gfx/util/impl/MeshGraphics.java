/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.momentum.gfx.util.impl;

import io.viki.momentum.util.perf.Analysis;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.buffer.BufferFrequency;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.util.Material;
import io.viki.momentum.gfx.util.Mesh;
import io.viki.momentum.gfx.util.MeshRecycledVertexStore;
import io.viki.momentum.gfx.util.Section;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.pipe.Pipeline;
import io.viki.momentum.gfx.pipe.Topology;
import io.viki.momentum.gfx.shader.ResourceSet;
import io.viki.momentum.gfx.shader.ResourceSetLayout;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.util.Primitive2D;
import io.viki.momentum.gfx.util.VertexStore;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Records 2D geometry into a retained-mode {@link Mesh} for later replay.
 *
 * <p>Unlike {@link BatchedGraphics}, which submits draw calls to the GPU on every flush,
 * {@code MeshGraphics} accumulates vertex and index data across flushes into section
 * drafts. Call {@link #bake(Device)} to produce an immutable {@link Mesh} that can be drawn
 * later via {@link Graphics#drawMesh(Mesh)}.
 *
 * <p>{@link #begin(RenderPass)} and {@link #end()} have no effect in this implementation;
 * render pass management is the caller's responsibility when drawing the baked mesh.
 *
 * <p>This builder is not thread-safe and should be used by one thread at a time.
 *
 * @see BatchedGraphics
 * @see Mesh
 */
public final class MeshGraphics extends BatchedGraphics {
  private final VertexStore defaultData;
  private final List<SectionDraft> drafts = new ArrayList<>();

  /**
   * Creates a new {@code MeshGraphics} backed by the given device.
   *
   * @param data   the staging area receiving vertices and indices
   * @param device the GPU device
   */
  public MeshGraphics(VertexStore data, Device device) {
    super(data, device);
    defaultData = data;
  }

  /**
   * Captures the recorded vertex and index data as a section draft, then clears the
   * staging buffers for the next batch. Does not submit anything to the GPU.
   *
   * @param force ignored; an empty batch is never recorded
   */
  @Override
  public void flush(boolean force) {
    if (!force && data.vertexCount() <= 0 && data.indexCount() <= 0) {
      return;
    }
    byte[] vertices = data.recordVertices();
    byte[] indices = data.recordIndices();
    int vertexOffset = data.vertices().readerIndex();
    int indexOffset = data.indices().readerIndex();
    int vertexBytes = data.vertices().readableBytes();
    int indexBytes = data.indices().readableBytes();
    if (vertexOffset != 0) {
      vertices = data.vertices().copiedArray();
      vertexOffset = 0;
    }
    if (indexOffset != 0) {
      indices = data.indices().copiedArray();
      indexOffset = 0;
    }
    data.clear();
    drafts.add(new SectionDraft(vertices, indices, vertexOffset, indexOffset, vertexBytes, indexBytes,
        currentPrimitive, currentTexture, sampler == null ? defSampler : sampler,
        resolvePipeline(), resolveResourceSetLayout()));
  }

  /**
   * No-op; render pass management is deferred until the baked mesh is drawn.
   *
   * @param pass the render pass descriptor; ignored
   */
  @Override
  public void begin(RenderPass pass) {
    begin(pass, null);
  }

  /**
   * Begins a mesh rebuild and reuses the previous mesh's CPU staging arrays when available.
   *
   * @param pass the render pass descriptor; ignored for retained meshes
   * @param previous the previous mesh to recycle, or {@code null} for the initial build
   */
  public void begin(RenderPass pass, @Nullable Mesh previous) {
    drafts.clear();
    if (previous == null) {
      setVertexStore(defaultData);
      data.clear();
    } else {
      setVertexStore(new MeshRecycledVertexStore(previous));
    }
  }

  /**
   * No-op; render pass management is deferred until the baked mesh is drawn.
   */
  @Override
  public void end() {
    flush(true);
  }

  /**
   * Converts all recorded section drafts into an immutable {@link Mesh}.
   *
   * <p>Each draft becomes a {@link Section} with its own vertex and index buffers, material,
   * and topology. Every baked mesh owns its own uniform buffer, so meshes cached from one
   * context can be created and closed independently — a shared ubo would be released by the
   * first {@link Mesh#close()}. The result can be drawn with
   * {@link Graphics#drawMesh(Mesh)}. Baking clears the builder so it can record a new mesh.
   *
   * @param device the GPU device to allocate buffers from
   * @return a new mesh containing all recorded geometry
   */
  public Mesh bake(Device device) {
    return finish().bake(device);
  }

  /**
   * Bakes the current geometry, updating compatible sections of {@code reuse} in place.
   *
   * @param device the GPU device that receives uploads
   * @param reuse the previous mesh to update, or {@code null} to allocate a mesh
   * @return the updated mesh
   */
  public Mesh bake(Device device, @Nullable Mesh reuse) {
    return finish().bake(device, reuse);
  }

  /**
   * Freezes the recorded CPU geometry without creating or uploading GPU resources.
   *
   * <p>The builder is cleared and can record more geometry after this call.
   *
   * @return an immutable draft that can later be baked into a mesh
   */
  Draft finish() {
    flush(true);
    Draft draft = new Draft(List.copyOf(drafts));
    drafts.clear();
    return draft;
  }

  /**
   * Holds a frozen geometry snapshot independently of the builder that created it.
   *
   * <p>A draft can be produced on a worker thread and baked into GPU resources on the context
   * thread.
   */
  static final class Draft {
    private final List<SectionDraft> drafts;

    private Draft(List<SectionDraft> drafts) {
      this.drafts = drafts;
    }

    Mesh bake(Device device) {
      return bake(device, null);
    }

    Mesh bake(Device device, @Nullable Mesh reuse) {
      Analysis.start("mesh.allocateBuffersAndQueueUploads");
      try {
        List<Section> sections = new ArrayList<>();
        List<Section> previous = reuse == null ? List.of() : reuse.sections();
        int previousIndex = 0;

        for (SectionDraft d : drafts) {
          if (d.primitive() == null) {
            continue;
          }
          Primitive2D primitive = d.primitive();
          Topology top = primitive.topology();
          int idxCount = d.indexBytes() / Integer.BYTES;
          int vertCount = d.vertexBytes() / primitive.vertexSize();
          Section old = previousIndex < previous.size() ? previous.get(previousIndex) : null;
          if (old != null && old.compatible(d.pipeline, d.rsl, top, primitive.isIndexed())) {
            ResourceSet rs = old.material().resourceSet();
            if (primitive.isTextured() && d.texture() != null && d.sampler() != null) {
              rs.bindTexture(1, d.texture(), d.sampler());
            }
            old.updateGeometry(d.vertices(), d.vertexBytes(), d.indices(), d.indexBytes(), vertCount, idxCount);
            sections.add(old);
            previousIndex++;
            continue;
          }

          ResourceSet rs = device.getResourceSet(d.rsl);
          boolean tex = d.primitive().isTextured();
          if (tex && d.texture() != null && d.sampler() != null) {
            rs.bindTexture(1, d.texture(), d.sampler());
          }

          BufferObject vbo = device.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STREAM));
          vbo.replace(d.vertices(), d.vertexBytes());
          BufferObject ibo = d.primitive().isIndexed() ? device.getBuffer(BufferObjectDesc.index(BufferFrequency.STREAM)) : null;
          if (ibo != null) {
            ibo.replace(d.indices(), d.indexBytes());
          }

          sections.add(new Section(new Material(d.pipeline, rs), vbo, ibo, vertCount, 0, 0, idxCount,
              top, d.vertices(), d.indices()));
          previousIndex++;
        }

        if (reuse == null) {
          return new Mesh(sections);
        }
        reuse.replaceSections(sections);
        return reuse;
      } finally {
        Analysis.end("mesh.allocateBuffersAndQueueUploads");
      }
    }
  }

  private record SectionDraft(byte[] vertices,
                              byte[] indices,
                              int vertexOffset,
                              int indexOffset,
                              int vertexBytes,
                              int indexBytes,
                              @Nullable Primitive2D primitive,
                              @Nullable Texture texture,
                              @Nullable Sampler sampler,
                              Pipeline pipeline,
                              ResourceSetLayout rsl) {
  }
}
