package net.fmhi.world.light;

/**
 * Represents a directional light beam or cone of light emitted from a source.
 *
 * <p>A beam is defined by its direction, angular spread, edge softness, range,
 * and brightness multiplier. It is typically used for spotlights, flashlights,
 * or directional light sources that cast light in a specific cone shape.
 *
 * <p>The beam's intensity falls off with distance according to the inverse
 * square law, modulated by the {@code strength} value.
 *
 * @param r         red channel
 * @param g         green channel
 * @param b         blue channel
 * @param direction the direction angle of the beam in radians, measured
 *                  from the positive X-axis, ranging from {@code 0} to
 *                  {@code 2π}. For example, {@code 0} points right,
 *                  {@code π/2} points up, {@code π} points left.
 * @param halfAngle the half-angle of the beam cone in radians. The total
 *                  beam angle is {@code 2 * halfAngle}. Must be in the
 *                  range {@code (0, π]}. A value of {@code π/2} produces
 *                  a 180-degree hemisphere, while smaller values create
 *                  tighter spotlights.
 * @param ambience  the softness of the beam's edges. Higher values produce
 *                  a smoother, more gradual falloff at the cone boundaries;
 *                  lower values create a sharper, more distinct cutoff.
 *                  Typically in the range {@code [0.0, 2.0]}.
 * @param range     the maximum reach of the beam in tiles. The beam's
 *                  intensity diminishes with distance and reaches zero
 *                  at this range. Must be positive.
 * @param strength  the brightness multiplier of the beam at its origin.
 *                  Higher values produce brighter light. Typically
 *                  clamped to the range {@code [0.0, 1.0]} for normalized
 *                  lighting, but may exceed {@code 1.0} for HDR rendering.
 * @see LightEngine
 * @see LightBuffer
 */
public record Beam(float r, float g, float b,
                   float direction, float halfAngle, float ambience, float range, float strength) {
}