package io.viki.momentum.gfx.ui.look;

import io.viki.momentum.gfx.text.Font;
import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.look.custom.*;
import io.viki.momentum.gfx.ui.present.Artworks;
import org.jspecify.annotations.Nullable;

/**
 * Factories for caller-owned texture skins. Install with Element.setRenderer or Look.register.
 * Renderers borrow their drawables and fonts; callers retain ownership of those resources.
 * Factory calls do not modify AutoLook. Use resulting renderers on the owning UI thread.
 */
public final class CustomLook {
  /** Matches the automatic editor's logical text size. */
  private static final float DEFAULT_TEXT_SIZE = 8.0F;
  /** Logical spacing used by the factory overloads; explicit constructors allow other values. */
  private static final int DEFAULT_PADDING = 4;
  /** Matches the automatic tooltip's spacing between rich-text entries. */
  private static final int DEFAULT_TOOLTIP_LINE_GAP = 2;

  private CustomLook() {
  }

  /**
   * Creates a text box skin with the requested font.
   *
   * @param background drawable behind editor content
   * @param font       editor font, or {@code null} to use the standard UI font
   * @return a renderer using this font for text and pointer hit testing
   */
  public static CustomTextBoxRenderer textBox(Drawable2D background, @Nullable Font font) {
    TextFormat format = TextFormat.of().size(DEFAULT_TEXT_SIZE);
    if (font != null) {
      format = format.font(font);
    }
    return new CustomTextBoxRenderer(background, format);
  }

  /**
   * Creates a button skin shared by all interaction states.
   *
   * @param background artwork used for every button state
   * @return a configured button renderer
   */
  public static CustomButtonRenderer button(Drawable2D background) {
    return button(background, background, background, background);
  }

  /**
   * Creates a button skin with separate interaction artwork.
   *
   * @param idle     artwork for the idle state
   * @param hovered  artwork for the hovered and focused states
   * @param pressed  artwork for the pressed state
   * @param disabled artwork for the disabled state
   * @return a configured button renderer
   */
  public static CustomButtonRenderer button(Drawable2D idle, Drawable2D hovered,
                                            Drawable2D pressed, Drawable2D disabled) {
    return new CustomButtonRenderer(idle, hovered, pressed, disabled);
  }

  /**
   * Creates a surface renderer for panels or other containers.
   *
   * @param background drawable used for the element surface
   * @return a configured surface renderer
   */
  public static CustomSurfaceRenderer panel(Drawable2D background) {
    return new CustomSurfaceRenderer(background);
  }

  /**
   * Creates a tooltip skin with the standard spacing.
   *
   * @param background drawable used behind tooltip text
   * @return a configured tooltip renderer
   */
  public static CustomTooltipRenderer tooltip(Drawable2D background) {
    return tooltip(background, DEFAULT_PADDING, DEFAULT_TOOLTIP_LINE_GAP);
  }

  /**
   * Creates a tooltip skin with explicit text spacing.
   *
   * @param background drawable used behind tooltip text
   * @param padding    inset from the background edge to the text
   * @param lineGap    vertical gap between adjacent entries
   * @return a configured tooltip renderer
   */
  public static CustomTooltipRenderer tooltip(Drawable2D background, int padding, int lineGap) {
    return new CustomTooltipRenderer(background, padding, lineGap);
  }

  /**
   * Creates a surface renderer applicable to any element type.
   *
   * @param background drawable used for the surface
   * @return a configured surface renderer
   */
  public static CustomSurfaceRenderer element(Drawable2D background) {
    return new CustomSurfaceRenderer(background);
  }

  /**
   * Creates a canvas surface renderer.
   *
   * @param background drawable used for the canvas
   * @return a configured surface renderer
   */
  public static CustomSurfaceRenderer canvas(Drawable2D background) {
    return new CustomSurfaceRenderer(background);
  }

  /**
   * Creates a group surface renderer.
   *
   * @param background drawable used for the group
   * @return a configured surface renderer
   */
  public static CustomSurfaceRenderer group(Drawable2D background) {
    return new CustomSurfaceRenderer(background);
  }

  /**
   * Creates a text view renderer while preserving rich text styling.
   *
   * @param background drawable placed behind the text
   * @return a configured text view renderer
   */
  public static CustomTextViewRenderer textView(Drawable2D background) {
    return new CustomTextViewRenderer(background);
  }

  /**
   * Creates an image view renderer that draws the view's current image over a background.
   *
   * @param background drawable placed behind the image
   * @return a configured image view renderer
   */
  public static CustomImageViewRenderer imageView(Drawable2D background) {
    return new CustomImageViewRenderer(background);
  }

  /**
   * Creates a checkbox skin with one drawable for each checked state.
   *
   * @param unchecked artwork for an unchecked box
   * @param checked   artwork for a checked box
   * @return a configured checkbox renderer
   */
  public static CustomCheckBoxRenderer checkBox(Drawable2D unchecked, Drawable2D checked) {
    return checkBox(new Artworks(unchecked), new Artworks(checked));
  }

  /**
   * Creates a checkbox skin with interaction artwork for both checked states.
   *
   * @param unchecked state artwork for an unchecked box
   * @param checked   state artwork for a checked box
   * @return a configured checkbox renderer
   */
  public static CustomCheckBoxRenderer checkBox(Artworks unchecked, Artworks checked) {
    return new CustomCheckBoxRenderer(unchecked, checked, DEFAULT_PADDING);
  }

  /**
   * Creates a slider skin with a single thumb drawable.
   *
   * @param track drawable for the track
   * @param thumb drawable for the thumb
   * @return a configured slider renderer
   */
  public static CustomSliderRenderer slider(Drawable2D track, Drawable2D thumb) {
    return slider(track, new Artworks(thumb));
  }

  /**
   * Creates a slider skin with state-specific thumb artwork.
   *
   * @param track drawable for the track
   * @param thumb state artwork for the thumb
   * @return a configured slider renderer
   */
  public static CustomSliderRenderer slider(Drawable2D track, Artworks thumb) {
    return new CustomSliderRenderer(track, thumb);
  }

  /**
   * Creates a scrollbar skin with a single thumb drawable.
   *
   * @param track drawable for the track
   * @param thumb drawable for the thumb
   * @return a renderer supporting either scrollbar orientation
   */
  public static CustomScrollBarRenderer scrollBar(Drawable2D track, Drawable2D thumb) {
    return scrollBar(track, new Artworks(thumb));
  }

  /**
   * Creates a scrollbar skin with state-specific thumb artwork.
   *
   * @param track drawable for the track
   * @param thumb state artwork for the thumb
   * @return a renderer supporting either scrollbar orientation
   */
  public static CustomScrollBarRenderer scrollBar(Drawable2D track, Artworks thumb) {
    return new CustomScrollBarRenderer(track, thumb);
  }

  /**
   * Creates a renderer for the fixed viewport surface.
   *
   * @param background drawable for the viewport
   * @return a configured scroll pane renderer
   */
  public static CustomScrollPaneRenderer scrollPane(Drawable2D background) {
    return new CustomScrollPaneRenderer(background);
  }

  /**
   * Creates a drop-down header skin with one drawable for every state.
   *
   * <p>Popup, option, and scrollbar skins are configured separately.
   *
   * @param background drawable for the header
   * @return a configured drop-down renderer
   */
  public static CustomDropDownRenderer dropDown(Drawable2D background) {
    return dropDown(new Artworks(background));
  }

  /**
   * Creates a drop-down header skin with state-specific artwork.
   *
   * @param background state artwork for the header
   * @return a configured drop-down renderer
   */
  public static CustomDropDownRenderer dropDown(Artworks background) {
    return new CustomDropDownRenderer(background, DEFAULT_PADDING);
  }

  /**
   * Creates a renderer for the drop-down popup surface.
   *
   * @param background drawable for the popup
   * @return a configured popup renderer
   */
  public static CustomDropDownPopupRenderer dropDownPopup(Drawable2D background) {
    return new CustomDropDownPopupRenderer(background);
  }

  /**
   * Creates a drop-down row skin with one drawable for every state.
   *
   * <p>The pressed drawable represents the selected option.
   *
   * @param background drawable for each option row
   * @return a configured option renderer
   */
  public static CustomDropDownOptionRenderer dropDownOption(Drawable2D background) {
    return dropDownOption(new Artworks(background));
  }

  /**
   * Creates a drop-down row skin with state-specific artwork.
   *
   * @param background state artwork for each option row
   * @return a configured option renderer
   */
  public static CustomDropDownOptionRenderer dropDownOption(Artworks background) {
    return new CustomDropDownOptionRenderer(background, DEFAULT_PADDING);
  }

  /**
   * Creates a window skin with one drawable for each title control.
   *
   * @param background drawable for the window body
   * @param titleBar   drawable for the title bar
   * @param close      drawable for the close control
   * @param minimize   drawable for the minimize control
   * @return a configured window renderer
   */
  public static CustomWindowRenderer window(Drawable2D background, Drawable2D titleBar,
                                            Drawable2D close, Drawable2D minimize) {
    return window(background, titleBar, new Artworks(close), new Artworks(minimize));
  }

  /**
   * Creates a window skin with state-specific title control artwork.
   *
   * @param background drawable for the window body
   * @param titleBar   drawable for the title bar
   * @param close      state artwork for the close control
   * @param minimize   state artwork for the minimize control
   * @return a configured window renderer
   */
  public static CustomWindowRenderer window(Drawable2D background, Drawable2D titleBar,
                                            Artworks close, Artworks minimize) {
    return new CustomWindowRenderer(background, titleBar, close, minimize, DEFAULT_PADDING);
  }
}
