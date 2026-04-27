namespace STS2Mobile.Patches.Shaders;

public static class BlurPatch
{
    private const string Path = "res://shaders/blur/Blur.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nconst float DEFAULT_RADIUS = 32.0;\nuniform vec2 step;\n"
        + "uniform float radius = 32.0;\n\nvoid fragment() {\n"
        + "    vec2 s = radius / DEFAULT_RADIUS * step / vec2(textureSize(TEXTURE, 0));\n"
        + "    COLOR.rgb =\n"
        + "        0.061 * texture(TEXTURE, UV - 28.0 * s).rgb +\n"
        + "        0.242 * texture(TEXTURE, UV - 12.0 * s).rgb +\n"
        + "        0.394 * texture(TEXTURE, UV).rgb +\n"
        + "        0.242 * texture(TEXTURE, UV + 12.0 * s).rgb +\n"
        + "        0.061 * texture(TEXTURE, UV + 28.0 * s).rgb;\n"
        + "    COLOR.a = 1.0;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "Blur (33 to 5)");
    }
}
