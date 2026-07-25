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

package net.fmhi.gfx.text.harfbuzz;

import com.ibm.icu.text.Bidi;
import net.fmhi.gfx.text.Font;
import net.fmhi.gfx.text.raster.ShapeResult;
import net.fmhi.util.InternalApi;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

/**
 * <p>Uses HarfBuzz for glyph shaping and ICU4J for bidirectional text analysis.
 * Font faces are cached to avoid repeated blob creation.
 */
@InternalApi
public final class HarfbuzzShaper implements AutoCloseable {
  // face handle → blob handle; both must be destroyed together when evicted
  private final Map<Font, long[]> faceCache = new HashMap<>();

  public ShapeResult shape(Font font, String text, float fontSize, boolean flipY, int fontStyle) {
    Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
    byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);

    long face;
    long[] entry = faceCache.get(font);
    if (entry != null) {
      face = entry[0];
    } else {
      long blob = hb_blob_create(font.fileData(), HB_MEMORY_MODE_READONLY, 0L, null);
      face = hb_face_create(blob, 0);
      faceCache.put(font, new long[] {face, blob});
    }

    long hbFont = hb_font_create(face);
    hb_font_set_scale(hbFont, (int) (fontSize * 64), (int) (fontSize * 64));

    long buf = hb_buffer_create();
    try {
      hb_buffer_guess_segment_properties(buf);
      hb_buffer_set_direction(buf, bidi.isLeftToRight() ? HB_DIRECTION_LTR : HB_DIRECTION_RTL);
      ByteBuffer utf8Buf = ByteBuffer.allocateDirect(utf8.length).put(utf8).flip();
      hb_buffer_add_utf8(buf, utf8Buf, 0, utf8.length);
      hb_shape(hbFont, buf, null);

      int gc = hb_buffer_get_length(buf);
      hb_glyph_info_t.Buffer infos = hb_buffer_get_glyph_infos(buf);
      hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(buf);
      assert infos != null;
      assert positions != null;

      int[] glyphs = new int[gc];
      int[] clusters = new int[gc];
      float[] advances = new float[gc];
      float[] offsets = new float[gc * 2];
      float ySign = flipY ? -1.0F : 1.0F;

      for (int i = 0; i < gc; i++) {
        hb_glyph_info_t info = infos.get(i);
        hb_glyph_position_t pos = positions.get(i);
        glyphs[i] = info.codepoint();
        clusters[i] = info.cluster();
        advances[i] = pos.x_advance() / 64.0F;
        offsets[i * 2] = pos.x_offset() / 64.0F;
        offsets[i * 2 + 1] = ySign * pos.y_offset() / 64.0F;
      }

      int textLen = text.length();
      int[] byteToChar = new int[utf8.length + 1];
      int ci = 0;
      for (int b = 0; b < utf8.length; ) {
        byteToChar[b] = ci;
        int cp = text.codePointAt(ci);
        b += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
        ci += Character.charCount(cp);
      }
      byteToChar[utf8.length] = textLen;

      int[] charIndices = new int[gc];
      for (int i = 0; i < gc; i++) {
        int bp = clusters[i];
        charIndices[i] = bp < byteToChar.length ? byteToChar[bp] : textLen - 1;
      }

      return new ShapeResult(glyphs, charIndices, advances, offsets);
    } finally {
      hb_buffer_destroy(buf);
      hb_font_destroy(hbFont);
    }
  }

  @Override
  public void close() {
    for (long[] entry : faceCache.values()) {
      hb_face_destroy(entry[0]);
      hb_blob_destroy(entry[1]);
    }
    faceCache.clear();
  }
}
