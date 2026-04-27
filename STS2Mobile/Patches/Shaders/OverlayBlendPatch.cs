namespace STS2Mobile.Patches.Shaders;

public static class OverlayBlendPatch
{
    private const string Path = "res://shaders/overlay_blend.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform sampler2D SCREEN_TEXTURE: hint_screen_texture;\n"
        + "uniform float intensity : hint_range(0,1) = 1.0;\n\n"
        + "vec4 overlay(vec4 base, vec4 blend) {\n"
        + "    vec4 limit = step(0.5, base);\n"
        + "    return mix(2.0 * base * blend, 1.0 - 2.0 * (1.0 - base) * (1.0 - blend), limit);\n}\n\n"
        + "void fragment() {\n"
        + "    vec4 bg_color = texture(SCREEN_TEXTURE, SCREEN_UV);\n"
        + "    COLOR.rgb = mix(COLOR, overlay(bg_color, COLOR), intensity).rgb;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "OverlayBlend (no change)");
    }
}
