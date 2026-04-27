namespace STS2Mobile.Patches.Shaders;

public static class CanvasGroupMaskBlurPatch
{
    private const string Path = "res://shaders/blur/canvas_group_mask_blur.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nconst vec2 size = vec2(598, 842);\n"
        + "const float DEFAULT_RADIUS = 32.0;\n\n"
        + "uniform sampler2D screen_texture : hint_screen_texture, repeat_disable, filter_nearest;\n"
        + "uniform sampler2D mask_texture : repeat_disable;\nuniform vec4 mask_region;\n"
        + "uniform vec2 mask_offset;\nuniform vec2 step;\nuniform float radius = 32.0;\n"
        + "uniform bool mask;\nuniform bool blur;\n\nvarying float rotation;\n\n"
        + "vec4 sample_screen(vec2 uv) {\n"
        + "    vec4 col = textureLod(screen_texture, uv, 0.0);\n"
        + "    if (col.a > 0.0001) { col.rgb /= col.a; }\n"
        + "    return col;\n}\n\n"
        + "vec2 shrink_rotated_canvas_group_uv(vec2 uv) {\n"
        + "    float pw = size.x + sin(2.0 * abs(rotation)) * size.y;\n"
        + "    float ph = size.y + sin(2.0 * abs(rotation)) * size.x;\n"
        + "    float sw = pw / size.x; float sh = ph / size.y;\n"
        + "    return uv * vec2(sw, sh) - vec2((sw - 1.0) / 2.0, (sh - 1.0) / 2.0);\n}\n\n"
        + "void vertex() {\n"
        + "    vec4 rc = MODEL_MATRIX * vec4(1, 0, 0, 0);\n"
        + "    rotation = atan(rc.y, rc.x);\n}\n\n"
        + "void fragment() {\n"
        + "    if (blur) {\n"
        + "        vec2 s = radius / DEFAULT_RADIUS * step / vec2(textureSize(screen_texture, 0));\n"
        + "        COLOR.rgb =\n"
        + "            0.061 * sample_screen(SCREEN_UV - 28.0 * s).rgb +\n"
        + "            0.242 * sample_screen(SCREEN_UV - 12.0 * s).rgb +\n"
        + "            0.394 * sample_screen(SCREEN_UV).rgb +\n"
        + "            0.242 * sample_screen(SCREEN_UV + 12.0 * s).rgb +\n"
        + "            0.061 * sample_screen(SCREEN_UV + 28.0 * s).rgb;\n"
        + "    } else {\n"
        + "        COLOR.rgb = sample_screen(SCREEN_UV).rgb;\n"
        + "    }\n\n"
        + "    if (mask) {\n"
        + "        vec2 ms = vec2(textureSize(mask_texture, 0));\n"
        + "        vec4 ss = sample_screen(SCREEN_UV);\n"
        + "        vec2 auv = clamp(shrink_rotated_canvas_group_uv(UV), 0, 1);\n"
        + "        vec2 mo = mask_offset / ms;\n"
        + "        vec2 mrs = mask_region.zw / ms + mo;\n"
        + "        vec2 mru = mask_region.xy / ms;\n"
        + "        vec2 muv = clamp(auv * mrs + mru - mo, mru, mru + mrs);\n"
        + "        COLOR.a *= ss.a * texture(mask_texture, muv).a;\n"
        + "    }\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "CanvasGroupMaskBlur (33 to 5)");
    }
}
