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

package io.viki.momentum.international;

import io.viki.momentum.util.QuickFmt;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A localization table mapping translation keys to translated text.
 *
 * <p>A {@code Language} is identified by a locale key such as {@code "en_us"}
 * or {@code "zh_cn"}. Translations are loaded from a string-string map whose
 * entries are key-value pairs of {@code translationKey → translatedText}.
 *
 * <p>Languages can be merged: when looking up a key, the primary language is
 * checked first, then the fallback chain. This enables base translations with
 * per-mod overrides.
 *
 * <p>This class is thread-safe for reads after registration.
 */
public final class Language {
  public static final String EN_US_KEY = "en_us";
  public static final Language EN_US = new Language(EN_US_KEY);
  private static volatile Language currentLanguage = EN_US;
  private static final Map<String, Language> LANGUAGES = new ConcurrentHashMap<>();
  private static final List<Consumer<Language>> CURRENT_LISTENERS = new CopyOnWriteArrayList<>();

  static {
    LANGUAGES.put(EN_US_KEY, EN_US);
  }

  private final String key;
  private final Map<String, String> translations;
  private final @Nullable Language fallback;

  private Language(String key, Map<String, String> translations, Language fallback) {
    this.key = key;
    this.translations = Map.copyOf(translations);
    this.fallback = fallback;
  }

  private Language(String key) {
    this.key = key;
    this.translations = new HashMap<>();
    this.fallback = EN_US;
  }

  /**
   * Returns the currently active language.
   *
   * @return current language
   */
  public static Language current() {
    return currentLanguage;
  }

  /**
   * Sets the global current language.
   *
   * <p>Listeners registered via {@link #addChangeListener(Consumer)} are
   * notified with the newly selected language.
   *
   * @param lang the language to set
   */
  public static void setCurrent(Language lang) {
    currentLanguage = lang;
    for (Consumer<Language> listener : CURRENT_LISTENERS) {
      listener.accept(lang);
    }
  }

  /**
   * Sets the global current language.
   *
   * <p>Listeners registered via {@link #addChangeListener(Consumer)} are
   * notified with the newly selected language.
   *
   * @param langKey the language key to set
   */
  public static void setCurrent(String langKey) {
    setCurrent(get(langKey));
  }

  /**
   * Registers a listener invoked whenever the current language changes.
   *
   * <p>UI systems can use this to refresh all displayed text.
   *
   * @param listener the listener, receiving the newly current language
   */
  public static void addChangeListener(Consumer<Language> listener) {
    CURRENT_LISTENERS.add(listener);
  }

  /**
   * Removes a change listener.

   * @param listener the listener, receiving the newly current language
   */
  public static void removeChangeListener(Consumer<Language> listener) {
    CURRENT_LISTENERS.remove(listener);
  }

  /**
   * Looks up a registered language by its locale key.
   *
   * @param key the locale key, e.g. {@code "en_us"}
   * @return the language, or {@code null} if not registered
   */
  public static Language get(String key) {
    return LANGUAGES.computeIfAbsent(key.toLowerCase(), Language::new);
  }

  /**
   * Loads a language from a string-string map.
   *
   * <p>Each entry in the tag is treated as a translation key-value pair.
   * Only entries with {@link String} values are kept; others are silently
   * skipped.
   *
   * @param tag the tag containing translation entries
   */
  public void merge(Iterable<Map.Entry<String, @Nullable String>> tag) {
    for (Map.Entry<String, String> entry : tag) {
      translations.put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Returns the locale key.
   *
   * @return the locale key
   */
  public String key() {
    return key;
  }

  /**
   * Creates a merged language where {@code other} overrides this one.
   *
   * <p>When translating, {@code other} is checked first; if the key is
   * not found, this language (and its fallback chain) is checked.
   *
   * @param other the overriding language
   * @return a new merged language
   */
  public Language merge(Language other) {
    return new Language(key, other.translations, this);
  }

  /**
   * Returns the translated text for the given key.
   *
   * <p>If the key is not found in this language, the fallback chain is
   * searched. If no translation is found anywhere in the chain, the key
   * itself is returned.
   *
   * @param translationKey the translation key
   * @param args           the formatting args
   * @return the translated text
   */
  public String translate(String translationKey, Object... args) {
    String value = translations.get(translationKey);
    if (value != null) {
      return QuickFmt.format(value, args);
    }
    if (fallback != null) {
      return fallback.translate(translationKey);
    }
    return translationKey;
  }

  @Override
  public String toString() {
    return key;
  }
}
