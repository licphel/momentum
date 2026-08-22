package net.fmhi.util;

import java.util.Objects;

/**
 * A lightweight string formatting utility that supports sequential replacement
 * of {@code {}} placeholders only.
 *
 * <p>Unlike {@link java.text.MessageFormat}, this utility does NOT support
 * indexed placeholders (e.g., {@code {0}}), complex formatting patterns,
 * or locale-specific formatting. It simply replaces {@code {}} placeholders
 * in order with the provided arguments.
 *
 * <p>{{ is treated as literal {.
 */
public final class QuickFmt {
  private QuickFmt() {
  }

  /**
   * Formats the template string by sequentially replacing {@code {}} placeholders
   * with the provided arguments.
   *
   * @param template the template string containing {@code {}} placeholders;
   *                 must not be {@code null}
   * @param args     the arguments to substitute into the placeholders in order;
   *                 may be {@code null} or empty, in which case the template
   *                 is returned unchanged
   * @return the formatted string with placeholders replaced
   */
  public static String format(String template, Object... args) {
    Objects.requireNonNull(template, "template cannot be null");

    if (args.length == 0) {
      return template;
    }

    int estimatedLength = template.length() + args.length * 8;
    StringBuilder sb = new StringBuilder(estimatedLength);

    int argIndex = 0;
    int len = template.length();

    for (int i = 0; i < len; i++) {
      char c = template.charAt(i);

      if (c == '{') {
        if (i + 1 < len && template.charAt(i + 1) == '{') {
          sb.append('{');
          i++;
          continue;
        }

        int end = template.indexOf('}', i + 1);
        if (end == -1) {
          sb.append('{');
          continue;
        }

        if (end == i + 1) {
          if (argIndex < args.length) {
            Object arg = args[argIndex++];
            sb.append(arg);
          } else {
            sb.append("{}");
          }
        } else {
          sb.append(template, i, end + 1);
        }
        i = end;
      } else {
        sb.append(c);
      }
    }

    return sb.toString();
  }
}