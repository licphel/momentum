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

import io.viki.momentum.js.JsRuntime;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A lightweight formatter with sequential JavaScript mapping expressions.
 *
 * <p>{@code {}} consumes the next argument and appends it unchanged. An arrow
 * expression such as {@code {v -> v > 1 ? "apples" : "apple"}} consumes the
 * next argument and appends the expression result. Both {@code ->} and the
 * JavaScript spelling {@code =>} are accepted. Any other non-empty expression
 * is evaluated as the body of {@code v => (body)}. A backslash before a brace
 * emits that brace literally; invalid expressions produce {@code [Bad Expr]}.
 *
 * <p>Expression functions are compiled once per thread and kept in that
 * thread's JavaScript runtime. The formatter is safe to call concurrently;
 * each thread owns its runtime and compiled values. Expressions are executable
 * code and should therefore come from trusted templates.
 */
public final class QuickFmt {
  private static final String BAD_EXPRESSION = "[Bad Expr]";
  private static final ThreadLocal<_TheJS> JSS = ThreadLocal.withInitial(_TheJS::new);

  private QuickFmt() {
  }

  /**
   * Formats a template by sequentially replacing placeholders with arguments.
   *
   * @param template template containing placeholders; must not be {@code null}
   * @param args     arguments consumed from left to right; may be empty
   * @return the formatted string
   */
  public static String format(String template, Object... args) {
    int estimatedLength = template.length() + args.length * 8;
    StringBuilder result = new StringBuilder(estimatedLength);
    int argument = 0;
    for (int index = 0; index < template.length(); index++) {
      char current = template.charAt(index);
      if (current == '\\' && index + 1 < template.length()) {
        char escaped = template.charAt(index + 1);
        if (escaped == '{' || escaped == '}') {
          result.append(escaped);
          index++;
          continue;
        }
      }
      if (current != '{') {
        result.append(current);
        continue;
      }

      int end = findEnd(template, index);
      if (end < 0) {
        result.append('{');
        continue;
      }
      String expression = template.substring(index + 1, end).trim();
      if (expression.isEmpty()) {
        if (argument < args.length) {
          result.append(args[argument++]);
        } else {
          result.append("{}");
        }
      } else {
        if (argument < args.length) {
          result.append(evaluate(expression, args[argument++]));
        } else {
          result.append(template, index, end + 1);
        }
      }
      index = end;
    }
    return result.toString();
  }

  private static @Nullable String evaluate(String expression, Object argument) {
    try {
      _TheJS state = JSS.get();
      Value function = state.functions.computeIfAbsent(expression, state::compile);
      Value value = function.execute(argument);
      return value.isNull() ? null : toJavaString(value);
    } catch (RuntimeException exception) {
      return BAD_EXPRESSION;
    }
  }

  private static String toJavaString(Value value) {
    if (value.isString()) {
      return value.asString();
    }
    if (value.isBoolean()) {
      return Boolean.toString(value.asBoolean());
    }
    return value.toString();
  }

  private static int findEnd(String template, int start) {
    int nestedBraces = 0;
    char quote = 0;
    boolean escaped = false;
    for (int index = start + 1; index < template.length(); index++) {
      char current = template.charAt(index);
      if (quote != 0) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == quote) {
          quote = 0;
        }
        continue;
      }
      if (current == '\'' || current == '"' || current == '`') {
        quote = current;
      } else if (current == '\\' && index + 1 < template.length()) {
        index++;
      } else if (current == '{') {
        nestedBraces++;
      } else if (current == '}' && nestedBraces-- == 0) {
        return index;
      }
    }
    return -1;
  }

  static class _TheJS {
    private final JsRuntime runtime = new JsRuntime();
    private final Map<String, Value> functions = new HashMap<>();

    private static int findArrow(String expression, String arrow) {
      char quote = 0;
      boolean escaped = false;
      for (int index = 0; index <= expression.length() - arrow.length(); index++) {
        char current = expression.charAt(index);
        if (quote != 0) {
          if (escaped) {
            escaped = false;
          } else if (current == '\\') {
            escaped = true;
          } else if (current == quote) {
            quote = 0;
          }
          continue;
        }
        if (current == '\'' || current == '"' || current == '`') {
          quote = current;
        } else if (expression.startsWith(arrow, index)) {
          return index;
        }
      }
      return -1;
    }

    private Value compile(String expression) {
      int arrow = findArrow(expression, "->");
      String source;
      if (arrow >= 0) {
        source = expression.substring(0, arrow) + "=>" + expression.substring(arrow + 2);
      } else if (findArrow(expression, "=>") >= 0) {
        source = expression;
      } else {
        source = "v => (" + expression + ")";
      }
      Value function = runtime.eval("quickfmt", "(" + source + ")");
      if (!function.canExecute()) {
        throw new IllegalArgumentException("QuickFmt expression is not executable: {" + expression + "}");
      }
      return function;
    }
  }
}
