namespace STS2Mobile.Patches.Shaders;

public static class DarkBlurPatch
{
    private const string Path = "res://shaders/dark_blur.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform float lod: hint_range(0.0, 5.0) = 5.0;\n"
        + "uniform sampler2D SCREEN_TEXTURE : hint_screen_texture, filter_linear_mipmap;\n"
        + "uniform float mix_percentage: hint_range(0.0, 1.0) = 0.3;\n\n"
        + "void fragment() {\n"
        + "    vec4 color = texture(SCREEN_TEXTURE, SCREEN_UV, min(lod, 3.0));\n"
        + "    color = mix(color, vec4(0, 0, 0, 1), mix_percentage);\n"
        + "    COLOR = color;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "DarkBlur (clamp LOD)");
    }
}
