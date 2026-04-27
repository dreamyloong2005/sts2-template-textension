namespace STS2Mobile.Patches.Shaders;

public static class WigglePatch
{
    private const string Path = "res://shaders/wiggle.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform sampler2D SCREEN_TEXTURE: hint_screen_texture, filter_linear;\n"
        + "uniform sampler2D NOISE_TEXTURE: repeat_enable;\n"
        + "uniform float strength: hint_range(0.0, 5, 0.1) = 1.0;\n"
        + "uniform float uv_scaling: hint_range(0.0, 1.0, 0.05) = 1.0;\n"
        + "uniform vec2 movement_direction = vec2(1, 0);\n"
        + "uniform float movement_speed: hint_range(0.0, 0.5, 0.01) = 0.1;\n\n"
        + "void fragment() {\n"
        + "    vec2 uv = SCREEN_UV;\n"
        + "    vec2 movement_factor = movement_direction * movement_speed * TIME;\n"
        + "    float noise_value = texture(NOISE_TEXTURE, uv * uv_scaling + movement_factor).r - 0.5;\n"
        + "    uv += noise_value * SCREEN_PIXEL_SIZE * strength;\n"
        + "    COLOR = texture(SCREEN_TEXTURE, uv);\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "Wiggle (remove extra sample)");
    }
}
