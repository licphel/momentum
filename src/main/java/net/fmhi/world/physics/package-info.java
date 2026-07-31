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

/**
 * Physics shapes and collision detection for world block interactions.
 *
 * <h2>Core types</h2>
 * <ul>
 *   <li>{@link net.fmhi.world.physics.Polygon} — immutable convex polygon
 *       with SAT and directional-SAT intersection</li>
 *   <li>{@link net.fmhi.world.physics.IntersectResult} — the result of a
 *       SAT intersection test, carrying the MTV when shapes overlap</li>
 * </ul>
 *
 * <h2>Usage for slope physics</h2>
 * <p>When an entity moves horizontally into sloped terrain, use
 * {@code directionalSatIntersection} to resolve collisions:
 * <ol>
 *   <li>Attempt horizontal separation with
 *       {@code directionalSatIntersection(other, velocity, false)}</li>
 *   <li>If that fails, attempt vertical separation with
 *       {@code directionalSatIntersection(other, (0, 1), true)} to slide
 *       the entity upward along the slope</li>
 * </ol>
 *
 * <h2>Collision geometry generation</h2>
 * <p>Use {@link Polygon#convexHull} to build collision polygons from tile
 * corner points, and {@link Polygon#clip} to compute the intersection of
 * collision shapes with tile boundaries.
 */
@NullMarked
package net.fmhi.world.physics;

import org.jspecify.annotations.NullMarked;
