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

package io.viki.momentum.js;

import io.viki.momentum.resource.Resource;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides an owner-thread-confined JavaScript environment for trusted scripts.
 *
 * <p>Scripts can access Java values exposed through {@link #bind(String, Object)} and
 * {@link #bindClass(String, Class)}. Create, use, and close a runtime on the same thread; values
 * returned by {@link #eval(String, String)} are invalid after the runtime is closed.
 */
public final class JsRuntime implements AutoCloseable {
  private final Context context;
  private final Map<String, Value> bindings = new HashMap<>();

  /** Creates an isolated JavaScript environment for trusted scripts. */
  public JsRuntime() {
    context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowHostClassLookup(name -> false)
        .build();
    context.getBindings("js").putMember("require", (ProxyExecutable) arguments -> {
      String name = arguments[0].asString();
      Value value = bindings.get(name);
      if (value == null) {
        throw new IllegalArgumentException("JS binding is not registered: " + name);
      }
      return value;
    });
  }

  /**
   * Exposes a host value to scripts under a name that can be resolved with {@code require}.
   *
   * @param name the name scripts use to retrieve the value
   * @param value the host value to expose
   * @return this runtime for chaining additional bindings
   */
  public JsRuntime bind(String name, Object value) {
    bindings.put(name, context.asValue(value));
    return this;
  }

  /**
   * Exposes a host class's static members to scripts under a name.
   *
   * @param name the name scripts use to retrieve the class
   * @param type the host class whose static members are exposed
   * @return this runtime for chaining additional bindings
   */
  public JsRuntime bindClass(String name, Class<?> type) {
    bindings.put(name, context.asValue(type).getMember("static"));
    return this;
  }

  /**
   * Evaluates JavaScript source in this runtime.
   *
   * @param name the source name used in diagnostics
   * @param source the JavaScript source to evaluate
   * @return the evaluated value, associated with this runtime
   * @throws PolyglotException if the source cannot be parsed or evaluation fails
   */
  public Value eval(String name, String source) {
    return context.eval(Source.newBuilder("js", source, name).buildLiteral());
  }

  /**
   * Reads and evaluates JavaScript source from a resource.
   *
   * @param resources the provider that supplies the source
   * @param path the provider-local path of the JavaScript source
   * @return the evaluated value, associated with this runtime
   * @throws RuntimeException if the resource is missing or cannot be read
   * @throws PolyglotException if the source cannot be parsed or evaluation fails
   */
  public Value eval(Resource resources, String path) {
    return eval(path, resources.readString(path));
  }

  @Override
  public void close() {
    context.close();
  }
}
