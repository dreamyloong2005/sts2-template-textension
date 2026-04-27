namespace STS2Mobile.Patches.Shaders;

public static class HsvPatch
{
    private const string Path = "res://shaders/hsv.gdshader";

    private const string Code =
        "\nshader_type canvas_item;\n\nuniform float h: hint_range(0,1) = 1;\n"
        + "uniform float s: hint_range(0,5) = 1;\nuniform float v = 1;\n\n"
        + "varying vec4 modulate_color;\n\nvoid vertex() {\n    modulate_color = COLOR;\n}\n\n"
        + "void fragment() {\n    vec4 col = texture(TEXTURE, UV);\n\n"
        + "    float hue = (1.0 - h) * 6.283185;\n"
        + "    float vsu = v * s * cos(hue);\n"
        + "    float vsw = v * s * sin(hue);\n\n"
        + "    vec3 ret;\n"
        + "    ret.r = (.299 * v + .701 * vsu + .168 * vsw) * col.r\n"
        + "        +   (.587 * v - .587 * vsu + .330 * vsw) * col.g\n"
        + "        +   (.114 * v - .114 * vsu - .497 * vsw) * col.b;\n"
        + "    ret.g = (.299 * v - .299 * vsu - .328 * vsw) * col.r\n"
        + "        +   (.587 * v + .413 * vsu + .035 * vsw) * col.g\n"
        + "        +   (.114 * v - .114 * vsu + .292 * vsw) * col.b;\n"
        + "    ret.b = (.299 * v - .300 * vsu + 1.25 * vsw) * col.r\n"
        + "        +   (.587 * v - .588 * vsu - 1.05 * vsw) * col.g\n"
        + "        +   (.114 * v + .886 * vsu - .203 * vsw) * col.b;\n\n"
        + "    COLOR = vec4(ret, col.a) * modulate_color;\n}\n";

    public static void Register()
    {
        ShaderPatchLoader.Register(Path, Code, "HSV (pre-baked coefficients)");
    }
}
