package net.fmhi.world.light;

/**
 * Represents a directional light beam or cone of light emitted from a source.
 *
 * <p>A beam is defined by its direction, angular spread, ambient contribution,
 * and intensity parameters. It is typically used for spotlights, flashlights,
 * or directional light sources that cast light in a specific cone shape.
 *
 * <p>The beam's intensity falls off with distance according to the inverse
 * square law, modulated by the {@code power} and {@code strength} values.
 *
 * @param direction the direction angle of the beam in radians, measured
 *                  from the positive X-axis, ranging from {@code 0} to
 *                  {@code 2π}. For example, {@code 0} points right,
 *                  {@code π/2} points up, {@code π} points left.
 * @param halfAngle the half-angle of the beam cone in radians. The total
 *                  beam angle is {@code 2 * halfAngle}. Must be in the
 *                  range {@code (0, π]}. A value of {@code π/2} produces
 *                  a 180-degree hemisphere, while smaller values create
 *                  tighter spotlights.
 * @param ambience  the ambient light contribution of the beam, typically
 *                  in the range {@code [0.0, 1.0]}. This represents the
 *                  minimum light level provided by the beam even when
 *                  outside the direct cone, useful for simulating
 *                  scattered or indirect light.
 * @param power     the intensity or brightness of the beam at its origin.
 *                  Higher values produce brighter light. Typically
 *                  clamped to the range {@code [0.0, 1.0]} for normalized
 *                  lighting, but may exceed {@code 1.0} for HDR rendering.
 * @param strength  the falloff rate or sharpness of the beam's edges.
 *                  Higher values create a sharper cutoff at the beam's
 *                  boundaries, while lower values produce softer, more
 *                  gradual edges. Typically in the range {@code [0.5, 2.0]}.
 * @see LightEngine
 * @see LightBuffer
 */
public record Beam(float direction, float halfAngle, float ambience, float power, float strength) {
}
