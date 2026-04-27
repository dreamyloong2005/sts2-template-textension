namespace STS2Mobile.Patches.Shaders;

public static class RadialBlurPatch
{
    private const string Path = "res://shaders/radial_blur.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform vec2 blur_center = vec2(0.45, 0.55);\n"
        + "uniform float blur_power : hint_range(0.0, 1.0) = 0.005;\n"
        + "uniform int sampling_count : hint_range(1, 64) = 12;\n"
        + "uniform sampler2D SCREEN_TEXTURE : hint_screen_texture, filter_linear_mipmap;\n\n"
        + "void fragment() {\n"
        + "    vec2 direction = SCREEN_UV - blur_center;\n"
        + "    float f = 1.0 / 6.0;\n"
        + "    vec3 c =\n"
        + "        texture(SCREEN_TEXTURE, SCREEN_UV).rgb * f +\n"
        + "        texture(SCREEN_TEXTURE, SCREEN_UV - blur_power * direction * 1.0).rgb * f +\n"
        + "        texture(SCREEN_TEXTURE, SCREEN_UV - blur_power * direction * 2.0).rgb * f +\n"
        + "        texture(SCREEN_TEXTURE, SCREEN_UV - blur_power * direction * 4.0).rgb * f +\n"
        + "        texture(SCREEN_TEXTURE, SCREEN_UV - blur_power * direction * 7.0).rgb * f +\n"
        + "        texture(SCREEN_TEXTURE, SCREEN_UV - blur_power * direction * 11.0).rgb * f;\n"
        + "    COLOR.rgb = c;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "RadialBlur (loop to 6 fixed)");
    }
}
