namespace STS2Mobile.Patches.Shaders;

public static class WaterReflectionPostPatch
{
    private const string Path = "res://shaders/vfx/vfx_water_reflection_post.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform sampler2D noise : repeat_enable;\n"
        + "uniform sampler2D mask;\n"
        + "uniform sampler2D SCREEN_TEXTURE : hint_screen_texture, filter_linear_mipmap;\n"
        + "uniform float speed : hint_range(0.002,0.5) = 0.1;\n"
        + "uniform float strength : hint_range(0.002,0.5) = 0.1;\n\n"
        + "void fragment() {\n"
        + "    float noise_value = texture(noise, UV + -TIME * speed).r;\n"
        + "    COLOR = texture(SCREEN_TEXTURE, SCREEN_UV - (strength / 2.0) + vec2(noise_value) * strength);\n"
        + "    COLOR.a *= texture(mask, UV).a;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "WaterReflectionPost (no change)");
    }
}
