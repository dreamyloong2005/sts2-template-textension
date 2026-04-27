namespace STS2Mobile.Patches.Shaders;

public static class InsatiableSandFallPatch
{
    private const string Path = "res://shaders/vfx/the_insatiable_sand_fall_2.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform sampler2D noise : repeat_enable;\n"
        + "uniform sampler2D mask;\n"
        + "uniform sampler2D SCREEN_TEXTURE : hint_screen_texture, filter_linear_mipmap;\n"
        + "uniform float speed : hint_range(0.002,1) = 0.1;\n"
        + "uniform float strength : hint_range(0.002,0.5) = 0.1;\n"
        + "uniform float angle_degrees : hint_range(0, 360) = 45.0;\n\n"
        + "void fragment() {\n"
        + "    float angle_rad = radians(angle_degrees);\n"
        + "    vec2 direction = vec2(cos(angle_rad), sin(angle_rad));\n"
        + "    float noise_value = texture(noise, UV + -TIME * speed * direction).r;\n"
        + "    COLOR = texture(SCREEN_TEXTURE, SCREEN_UV - (strength / 2.0) + vec2(noise_value) * strength);\n"
        + "    COLOR.a *= texture(mask, UV).a;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "InsatiableSandFall (cleanup)");
    }
}
