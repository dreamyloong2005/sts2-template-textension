namespace STS2Mobile.Patches.Shaders;

public static class ScreamDistortionPolarPatch
{
    private const string Path =
        "res://shaders/vfx/scream/vfx_scream_distortion_polar_shader.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\nrender_mode blend_mix;\n\n"
        + "uniform sampler2D screen_texture : hint_screen_texture, repeat_disable, filter_linear;\n"
        + "uniform float distortion_base_intensity = 0.05;\n"
        + "uniform vec2 tiling = vec2(1.0, 1.0);\nuniform vec2 offset_speed;\n\n"
        + "varying vec4 vertex_color;\n\nvoid vertex() {\n"
        + "    vertex_color = COLOR;\n}\n\nvoid fragment() {\n"
        + "    vec2 centered = (UV - 0.5) * 2.0;\n"
        + "    float angle = atan(centered.y, centered.x) / (2.0 * PI);\n"
        + "    float dist = length(centered);\n"
        + "    vec2 polar_uv = vec2(angle, dist) * tiling + TIME * offset_speed;\n\n"
        + "    vec2 direction = vec2(0.5, 0.5) - UV;\n"
        + "    float distortion_mask = texture(TEXTURE, UV).a;\n"
        + "    float distortion_polar_mask = texture(TEXTURE, polar_uv).r;\n"
        + "    float distortion_intensity = vertex_color.a;\n\n"
        + "    vec4 screen_tex = textureLod(\n"
        + "        screen_texture,\n"
        + "        SCREEN_UV + (distortion_base_intensity * distortion_intensity * distortion_mask * distortion_polar_mask * direction),\n"
        + "        0.0);\n\n"
        + "    COLOR = screen_tex;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "ScreamDistortionPolar (simplify)");
    }
}
