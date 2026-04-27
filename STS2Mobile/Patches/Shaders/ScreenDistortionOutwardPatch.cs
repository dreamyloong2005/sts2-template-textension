namespace STS2Mobile.Patches.Shaders;

public static class ScreenDistortionOutwardPatch
{
    private const string Path =
        "res://shaders/vfx/distortion/vfx_screen_distortion_outward_shader.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\nrender_mode blend_mix;\n\n"
        + "uniform sampler2D screen_texture : hint_screen_texture, repeat_disable, filter_linear;\n"
        + "uniform float distortion_base_intensity = 0.05;\n\n"
        + "varying vec4 vertex_color;\nvarying float lifetime;\n\nvoid vertex() {\n"
        + "    vertex_color = COLOR;\n"
        + "    lifetime = INSTANCE_CUSTOM.y / INSTANCE_CUSTOM.w;\n}\n\n"
        + "void fragment() {\n"
        + "    vec2 direction = vec2(0.5, 0.5) - UV;\n"
        + "    float distortion_mask = texture(TEXTURE, UV).r;\n"
        + "    float distortion_intensity = vertex_color.a;\n\n"
        + "    vec4 screen_tex = textureLod(\n"
        + "        screen_texture,\n"
        + "        SCREEN_UV + (distortion_base_intensity * distortion_intensity * distortion_mask * direction),\n"
        + "        0.0);\n\n"
        + "    COLOR = screen_tex;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "ScreenDistortionOutward (skip normalize)");
    }
}
