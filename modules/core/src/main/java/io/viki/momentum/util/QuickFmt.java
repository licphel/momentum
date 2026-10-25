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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.momentum.util;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formats brace-delimited text with reusable compiled templates.
 *
 * <p>Each placeholder consumes one argument in order, exposed to its expression as {@code v}.
 * Compiled templates are cached and can be reused concurrently. Formatting with no arguments
 * preserves placeholders as literal text while unescaping escaped braces.
 *
 * <p>This class is thread-safe.
 */
public final class QuickFmt {
  /** Reuses compiled templates keyed by their source text. */
  private static final ConcurrentHashMap<String, Template> CACHE = new ConcurrentHashMap<>();

  private QuickFmt() {
  }

  /**
   * Formats source text using the supplied arguments.
   *
   * <p>Arguments are assigned to placeholders in order. Placeholders without a corresponding
   * argument remain unchanged; additional arguments are ignored.
   *
   * @param source text containing literal text and optional placeholders
   * @param args values assigned to placeholders
   * @return formatted text, with expression evaluation failures rendered as {@code [Bad Expr]}
   * @throws IllegalArgumentException if a placeholder expression is invalid and arguments are supplied
   */
  public static String format(String source, Object... args) {
    if (args.length == 0) {
      return source.replace("\\{", "{").replace("\\}", "}");
    }
    return compile(source).format(args);
  }

  /**
   * Compiles source text into a reusable formatting template.
   *
   * @param source text containing literal text and optional placeholders
   * @return the cached or newly compiled template
   * @throws IllegalArgumentException if a placeholder contains an invalid expression
   */
  public static Template compile(String source) {
    return CACHE.computeIfAbsent(source, QuickFmt::parse);
  }

  /**
   * Parses source text into literal sections and argument placeholders.
   *
   * @param source template source
   * @return the parsed template
   * @throws IllegalArgumentException if a delimited placeholder contains an invalid expression
   */
  private static Template parse(String source) {
    var parts = new ArrayList<Part>();
    var literal = new StringBuilder();
    int argument = 0;
    for (int i = 0; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (ch == '\\' && i + 1 < source.length()
          && (source.charAt(i + 1) == '{' || source.charAt(i + 1) == '}')) {
        literal.append(source.charAt(++i));
        continue;
      }
      int end = ch == '{' ? expressionEnd(source, i + 1) : -1;
      if (end < 0) {
        literal.append(ch);
        continue;
      }
      if (!literal.isEmpty()) {
        parts.add(new Part(literal.toString(), -1, null));
        literal.setLength(0);
      }
      String expression = source.substring(i + 1, end).trim();
      try {
        parts.add(new Part(source.substring(i, end + 1), argument++,
            expression.isEmpty() ? null : Engine.INSTANCE.createExpression(expression)));
      } catch (JexlException exception) {
        throw new IllegalArgumentException("Invalid format expression in: " + source, exception);
      }
      i = end;
    }
    if (!literal.isEmpty()) {
      parts.add(new Part(literal.toString(), -1, null));
    }
    return new Template(parts.toArray(Part[]::new));
  }

  /**
   * Finds the closing brace for an expression beginning at the supplied source index.
   *
   * @param source complete template source
   * @param start index immediately after the opening brace
   * @return the matching closing-brace index, or {@code -1} if none exists
   */
  private static int expressionEnd(String source, int start) {
    char quote = 0;
    int depth = 0;
    for (int i = start; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (quote != 0) {
        if (ch == '\\') {
          i++;
        } else if (ch == quote) {
          quote = 0;
        }
      } else if (ch == '\'' || ch == '"' || ch == '`') {
        quote = ch;
      } else if (ch == '{') {
        depth++;
      } else if (ch == '}' && depth-- == 0) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Immutable compiled formatting template.
   *
   * <p>Each call evaluates placeholders with its own argument context, so a template can be shared
   * safely between concurrent callers.
   */
  public static final class Template {
    private final Part[] parts;

    private Template(Part[] parts) {
      this.parts = parts;
    }

    /**
     * Formats this template with the supplied arguments.
     *
     * <p>Each placeholder consumes one argument in order. Missing arguments leave their
     * placeholders unchanged, extra arguments are ignored, and expression evaluation failures
     * are rendered as {@code [Bad Expr]}.
     *
     * @param args values assigned to placeholders
     * @return formatted text
     */
    public String format(Object... args) {
      var result = new StringBuilder();
      ArgumentContext context = null;
      for (Part part : parts) {
        if (part.argument < 0 || part.argument >= args.length) {
          result.append(part.text);
        } else if (part.expression == null) {
          result.append(args[part.argument]);
        } else {
          if (context == null) {
            context = new ArgumentContext();
          }
          context.value = args[part.argument];
          try {
            result.append(part.expression.evaluate(context));
          } catch (JexlException exception) {
            result.append("[Bad Expr]");
          }
        }
      }
      return result.toString();
    }
  }

  private record Part(String text, int argument, @Nullable JexlExpression expression) {
  }

  /** Exposes the current placeholder argument as a read-only {@code v} binding. */
  private static final class ArgumentContext implements JexlContext {
    /** Value exposed to the expression currently being evaluated. */
    private @Nullable Object value;

    /**
     * Returns the current argument for the formatter's {@code v} variable.
     *
     * @param name expression variable name
     * @return the current argument for {@code v}, or {@code null} for any other name
     */
    @Override
    public @Nullable Object get(String name) {
      return name.equals("v") ? value : null;
    }

    /**
     * Reports whether the formatter exposes the requested expression variable.
     *
     * @param name expression variable name
     * @return {@code true} only for {@code v}
     */
    @Override
    public boolean has(String name) {
      return name.equals("v");
    }

    /**
     * Rejects writes because format arguments are read-only during evaluation.
     *
     * @param name expression variable name
     * @param value proposed value, which is not stored
     * @throws UnsupportedOperationException always, because the context is read-only
     */
    @Override
    public void set(String name, @Nullable Object value) {
      throw new UnsupportedOperationException("Format arguments are read-only: " + name);
    }
  }

  /** Holds the shared expression engine restricted to formatter helpers. */
  private static final class Engine {
    /** Expression engine reused by all compiled templates. */
    private static final JexlEngine INSTANCE = new JexlBuilder().cache(512).strict(true).silent(false)
        .permissions(new JexlPermissions.ClassPermissions(JexlPermissions.SECURE, Functions.class))
        .namespaces(Collections.singletonMap(null, Functions.class)).create();
  }

  /**
   * Stateless formatting helpers available to placeholder expressions.
   *
   * <p>These helpers do not retain caller state and may be used concurrently.
   */
  public static final class Functions {
    /** Highest supported number of fractional digits for {@link #fixed(Number, int)}. */
    private static final int MAX_FRACTION_DIGITS = 16;

    private Functions() {
    }

    /**
     * Formats a number with a fixed number of fractional digits.
     *
     * <p>Finite values are rounded using half-up rounding; non-finite values use their standard
     * floating-point text representation.
     *
     * @param number value to format
     * @param precision number of fractional digits, from zero through sixteen
     * @return the formatted number
     * @throws IllegalArgumentException if {@code precision} is outside the supported range
     */
    public static String fixed(Number number, int precision) {
      if (precision < 0 || precision > MAX_FRACTION_DIGITS) {
        throw new IllegalArgumentException("Fraction digits must be between 0 and " + MAX_FRACTION_DIGITS);
      }
      double value = number.doubleValue();
      // BigDecimal cannot represent infinity or NaN.
      return Double.isFinite(value)
          ? BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP).toPlainString()
          : Double.toString(value);
    }
  }
}
