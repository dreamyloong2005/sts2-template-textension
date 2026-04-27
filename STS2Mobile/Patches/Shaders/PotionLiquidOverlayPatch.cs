namespace STS2Mobile.Patches.Shaders;

public static class PotionLiquidOverlayPatch
{
    private const string Path = "res://shaders/vfx/potion/vfx_potion_liquid_overlay_shader.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\nrender_mode blend_mix;\n\nuniform sampler2D overlay_texture : repeat_enable;\n"
        + "uniform float overlay_influence;\nuniform vec2 distortion_panning_speed;\n"
        + "uniform float distortion_strength;\nuniform vec2 overlay_panning_speed;\n"
        + "uniform vec4 tint : source_color = vec4(1.0);\nuniform sampler2D lut;\n"
        + "uniform float h: hint_range(0,1) = 1;\nuniform float s: hint_range(0,5) = 1;\nuniform float v = 1;\n\n"
        + "vec4 get_hue_shifted_color(vec4 col, vec4 base_color) {\n"
        + "    vec4 modular_col = base_color / col;\n\n"
        + "    float hue = (1.0 - h) * 6.283185;\n"
        + "    float vsu = v * s * cos(hue);\n"
        + "    float vsw = v * s * sin(hue);\n\n"
        + "    vec3 ret;\n"
        + "    ret.r = (.299 * v + .701 * vsu + .168 * vsw) * col.r\n"
        + "        +   (.587 * v - .587 * vsu + .330 * vsw) * col.g\n"
        + "        +   (.114 * v - .114 * vsu - .497 * vsw) * col.b;\n"
        + "    ret.g = (.299 * v - .299 * vsu - .328 * vsw) * col.r\n"
        + "        +   (.587 * v + .413 * vsu + .035 * vsw) * col.g\n"
        + "        +   (.114 * v - .114 * vsu + .292 * vsw) * col.b;\n"
        + "    ret.b = (.299 * v - .300 * vsu + 1.25 * vsw) * col.r\n"
        + "        +   (.587 * v - .588 * vsu - 1.05 * vsw) * col.g\n"
        + "        +   (.114 * v + .886 * vsu - .203 * vsw) * col.b;\n\n"
        + "    return vec4(ret, col.a) * modular_col;\n}\n\n"
        + "void fragment() {\n"
        + "    vec4 distortion_tex = texture(overlay_texture, (SCREEN_UV * 10.0) + (TIME * distortion_panning_speed));\n"
        + "    vec4 overlay_color = texture(overlay_texture, (SCREEN_UV * 10.0) + (TIME * overlay_panning_speed) + (distortion_tex.b * distortion_strength));\n\n"
        + "    vec4 lut_tex = texture(lut, vec2(overlay_color.r)) * tint;\n"
        + "    vec4 base_tex = texture(TEXTURE, UV);\n"
        + "    vec4 hue_shifted_color = get_hue_shifted_color(base_tex, COLOR);\n\n"
        + "    float nontransparent_base_color = step(0.5, hue_shifted_color.a);\n"
        + "    COLOR = vec4(mix(hue_shifted_color, lut_tex, overlay_color.a * nontransparent_base_color * overlay_influence).rgb, hue_shifted_color.a);\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "PotionLiquidOverlay (pre-baked coefficients)");
    }
}
