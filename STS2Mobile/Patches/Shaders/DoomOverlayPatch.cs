namespace STS2Mobile.Patches.Shaders;

public static class DoomOverlayPatch
{
    private const string Path = "res://shaders/doom_overlay.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform sampler2D SCREEN_TEXTURE : hint_screen_texture, filter_linear_mipmap;\n"
        + "uniform float s: hint_range(0,5) = 1;\nuniform float v = 1;\n"
        + "uniform vec4 gradient_color : source_color;\n\nvoid fragment() {\n"
        + "    vec4 color = texture(SCREEN_TEXTURE, SCREEN_UV);\n\n"
        + "    float sat = mix(1.0, s, COLOR.a);\n"
        + "    float val = mix(1.0, v, COLOR.a);\n"
        + "    float luma = dot(color.rgb, vec3(0.2989, 0.5870, 0.1140));\n"
        + "    color.rgb = mix(vec3(luma), color.rgb, sat) * val;\n\n"
        + "    float flicker = smoothstep(-10.0, 1.0, sin(TIME * 20.0) + sin(TIME * 10.0));\n"
        + "    float y = pow(SCREEN_UV.g, 6.0) * 0.7 * COLOR.a * flicker;\n\n"
        + "    COLOR = color + y * gradient_color;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "DoomOverlay (luma desaturate)");
    }
}
