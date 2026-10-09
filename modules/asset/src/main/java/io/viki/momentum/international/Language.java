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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.viki.momentum.util.Identifier;
import io.viki.momentum.util.Namespace;
import io.viki.momentum.util.QuickFmt;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Global localization registry. Tables are immutable snapshots, safely published to readers.
 * Change listeners run on the thread selecting the language.
 */
public final class Language {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, Language> LANGUAGES = new ConcurrentHashMap<>();
  private static final List<Consumer<Language>> CURRENT_LISTENERS = new CopyOnWriteArrayList<>();
  private static volatile Language currentLanguage;

  public static final Language EN = register("en", "English");

  static {
    String[][] defaults = {
        {"ar", "العربية"},
        {"bg", "Български"},
        {"zh-CN", "简体中文"},
        {"zh-TW", "繁體中文"},
        {"cs", "Čeština"},
        {"da", "Dansk"},
        {"nl", "Nederlands"},
        {"fi", "Suomi"},
        {"fr", "Français"},
        {"de", "Deutsch"},
        {"el", "Ελληνικά"},
        {"hu", "Magyar"},
        {"id", "Bahasa Indonesia"},
        {"it", "Italiano"},
        {"ja", "日本語"},
        {"ko", "한국어"},
        {"ms", "Bahasa Melayu"},
        {"no", "Norsk"},
        {"pl", "Polski"},
        {"pt", "Português"},
        {"pt-BR", "Português (Brasil)"},
        {"ro", "Română"},
        {"ru", "Русский"},
        {"es", "Español (España)"},
        {"es-419", "Español (Latinoamérica)"},
        {"sv", "Svenska"}, {"th", "ไทย"},
        {"tr", "Türkçe"},
        {"uk", "Українська"},
        {"vi", "Tiếng Việt"}
    };
    for (String[] entry : defaults) {
      register(entry[0], entry[1]);
    }
    currentLanguage = EN;
  }

  private final String key;
  private volatile String name;
  private volatile Map<Identifier, String> translations = Map.of();

  private Language(String key, String name) {
    this.key = key;
    this.name = name;
  }

  /**
   * Registers a language independently of whether translations are available.
   * Re-registering a case-insensitively equal key updates its display name, preserving its table.
   *
   * @param key  locale code; the first registered spelling is retained
   * @param name display name for language selection
   * @return the stable registered language instance
   */
  public static Language register(String key, String name) {
    Language language = LANGUAGES.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> new Language(key, name));
    language.name = name;
    return language;
  }

  /**
   * Resolves a locale code, registering an empty language if it is not yet known.
   *
   * @param key case-insensitive locale code
   * @return the stable language instance; newly discovered languages use their code as their name
   */
  public static Language get(String key) {
    return LANGUAGES.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> new Language(key, key));
  }

  /**
   * Provides registered languages for selection, including those without translation files.
   *
   * @return an immutable snapshot sorted by locale code
   */
  public static Collection<Language> languages() {
    return LANGUAGES.values().stream().sorted(Comparator.comparing(Language::key)).toList();
  }

  /**
   * Returns the globally selected language.
   *
   * @return the active language
   */
  public static Language current() {
    return currentLanguage;
  }

  /**
   * Selects the global language and synchronously notifies change listeners on the calling thread.
   * Listener exceptions propagate to the caller after the language has been selected.
   *
   * @param language language to select
   */
  public static void setCurrent(Language language) {
    currentLanguage = language;
    for (Consumer<Language> listener : CURRENT_LISTENERS) {
      listener.accept(language);
    }
  }

  /**
   * Resolves and selects a language, notifying listeners as in {@link #setCurrent(Language)}.
   *
   * @param key case-insensitive locale code, registered if previously unknown
   */
  public static void setCurrent(String key) {
    setCurrent(get(key));
  }

  /**
   * Subscribes to subsequent language selections, not translation insertions or downloads.
   *
   * @param listener callback receiving the selected language on the selecting thread
   */
  public static void addChangeListener(Consumer<Language> listener) {
    CURRENT_LISTENERS.add(listener);
  }

  /**
   * Removes one registration of a language-selection listener.
   *
   * @param listener callback to unsubscribe
   */
  public static void removeChangeListener(Consumer<Language> listener) {
    CURRENT_LISTENERS.remove(listener);
  }

  /**
   * Adds or replaces a translation in the language.
   * Other translations and languages are preserved.
   *
   * @param key   namespace-qualified translation key
   * @param value translated text, optionally containing {@link QuickFmt} placeholders
   */
  public void insert(Identifier key, String value) {
    putAll(Map.of(key, value));
  }

  /**
   * Imports a flat JSON object of translation paths and text into the language.
   * All entries belong to the supplied namespace; malformed input leaves the table unchanged.
   *
   * @param namespace namespace assigned to every JSON property
   * @param json      complete JSON object whose values must be strings
   * @throws IllegalArgumentException if the input is malformed, contains trailing content,
   *                                  is not an object, or contains non-string values
   */
  public void load(Namespace namespace, String json) {
    putAll(parse(namespace, json));
  }

  /**
   * Translates using the active language, its registered base locales, and finally English.
   * Formatting arguments are preserved across fallbacks; a missing translation returns the key.
   *
   * @param key  namespace-qualified translation key
   * @param args arguments for {@link QuickFmt} placeholders
   * @return formatted translation, or the identifier string if no translation exists
   */
  public static String translate(Identifier key, Object... args) {
    Language language = currentLanguage;
    String value = language.find(key);
    return value == null ? key.toString() : QuickFmt.format(value, args);
  }

  /**
   * Parses an import without changing a language, preventing partially applied invalid content.
   *
   * @param namespace namespace assigned to the parsed keys
   * @param json      flat JSON translation object
   * @return parsed translation entries
   * @throws IllegalArgumentException if the JSON is not a complete object of string values
   */
  private static Map<Identifier, String> parse(Namespace namespace, String json) {
    try {
      JsonNode root = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .readTree(json);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Language JSON must be an object");
      }
      Map<Identifier, String> entries = new HashMap<>();
      for (Map.Entry<String, JsonNode> entry : root.properties()) {
        if (!entry.getValue().isTextual()) {
          throw new IllegalArgumentException("Language value must be text: " + entry.getKey());
        }
        entries.put(new Identifier(namespace, entry.getKey()), entry.getValue().textValue());
      }
      return entries;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid language JSON for " + namespace, e);
    }
  }

  /**
   * Asynchronously imports the latest file content at a GitHub branch, tag, or commit.
   * The language selected at invocation remains the target even if the selection later changes.
   * Transfer limits and failure behavior are defined by {@link #update(Namespace, URI)}.
   *
   * @param namespace  namespace assigned to the downloaded translation keys
   * @param repository GitHub repository in owner/name form
   * @param ref        branch, tag, or commit expressed as a URL path component
   * @param path       repository-relative JSON path expressed as a URL path
   * @return future containing {@code true} after successful import, otherwise {@code false}
   * @throws IllegalArgumentException if the arguments form an invalid URI
   */
  public static CompletableFuture<Boolean> updateFromGitHub(Namespace namespace, String repository, String ref, String path) {
    return update(namespace, URI.create("https://raw.githubusercontent.com/" + repository + "/" + ref + "/" + path));
  }

  /**
   * Asynchronously downloads and imports a raw UTF-8 JSON resource into the language active now.
   * A transfer is abandoned if its first body byte takes more than 1500 milliseconds or a
   * subsequent 500-millisecond measurement window averages less than 128 KiB/s.
   * Small responses completed before the first speed measurement are accepted.
   * Network, HTTP, URI, and JSON failures produce {@code false} without changing local content.
   * Successful imports replace matching keys but preserve unrelated translations.
   *
   * @param namespace namespace assigned to every downloaded translation key
   * @param content   HTTP or HTTPS URI serving raw JSON, not an HTML repository page
   * @return future containing {@code true} after publication, otherwise {@code false}
   */
  public static CompletableFuture<Boolean> update(Namespace namespace, URI content) {
    Language target = currentLanguage;
    return CompletableFuture.supplyAsync(() -> {
      try {
        target.putAll(parse(namespace, LanguageDownload.fetch(content)));
        return true;
      } catch (IOException | IllegalArgumentException e) {
        return false;
      }
    });
  }

  /**
   * Returns the locale code in its originally registered spelling.
   *
   * @return locale code
   */
  public String key() {
    return key;
  }

  /**
   * Returns the language-selection display name.
   *
   * @return current display name
   */
  public String name() {
    return name;
  }

  /**
   * Resolves text through regional and English fallbacks without formatting it.
   *
   * @param id translation identifier
   * @return translated template, or {@code null} if all eligible languages lack it
   */
  private @Nullable String find(Identifier id) {
    String value = translations.get(id);
    if (value != null) {
      return value;
    }
    int separator = key.lastIndexOf('-');
    if (separator > 0) {
      Language base = LANGUAGES.get(key.substring(0, separator).toLowerCase(Locale.ROOT));
      if (base != null) {
        value = base.find(id);
        if (value != null) {
          return value;
        }
      }
    }
    return this == EN ? null : EN.translations.get(id);
  }

  /**
   * Publishes a complete import while preserving concurrent updates to unrelated entries.
   *
   * @param entries translations to add or replace
   */
  private synchronized void putAll(Map<Identifier, String> entries) {
    Map<Identifier, String> updated = new HashMap<>(translations);
    updated.putAll(entries);
    translations = Map.copyOf(updated);
  }

  @Override
  public String toString() {
    return key;
  }
}
