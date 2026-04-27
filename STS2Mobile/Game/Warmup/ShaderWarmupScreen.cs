using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Threading.Tasks;
using Godot;
using STS2Mobile.Patches.Shaders;

namespace STS2Mobile.Game.Warmup;

public sealed class ShaderWarmupScreen : CanvasLayer
{
    private const int BatchSize = 8;

    private TaskCompletionSource<bool> _completion;
    private Label _titleLabel;
    private Label _messageLabel;
    private Label _detailLabel;
    private ProgressBar _progressBar;
    private Button _confirmButton;

    public Task WaitForCompletion()
    {
        _completion = new TaskCompletionSource<bool>();
        return _completion.Task;
    }

    public void Initialize()
    {
        BuildUi();
        Callable.From(RunWarmup).CallDeferred();
    }

    private void BuildUi()
    {
        Layer = 1000;

        var background = new ColorRect
        {
            Color = new Color(0.02f, 0.02f, 0.025f, 0.96f),
            MouseFilter = Control.MouseFilterEnum.Stop,
        };
        background.SetAnchorsPreset(Control.LayoutPreset.FullRect);
        AddChild(background, forceReadableName: false, InternalMode.Disabled);

        var panel = new PanelContainer
        {
            OffsetLeft = -360,
            OffsetTop = -180,
            OffsetRight = 360,
            OffsetBottom = 180,
        };
        panel.SetAnchorsPreset(Control.LayoutPreset.Center);
        AddChild(panel, forceReadableName: false, InternalMode.Disabled);

        var box = new VBoxContainer
        {
            CustomMinimumSize = new Vector2(640, 300),
        };
        box.AddThemeConstantOverride("separation", 14);
        panel.AddChild(box, forceReadableName: false, InternalMode.Disabled);

        _titleLabel = new Label
        {
            Text = "预热着色器 / Shader Warmup",
            HorizontalAlignment = HorizontalAlignment.Center,
        };
        _titleLabel.AddThemeFontSizeOverride("font_size", 28);
        box.AddChild(_titleLabel, forceReadableName: false, InternalMode.Disabled);

        _messageLabel = new Label
        {
            Text = "正在准备...",
            HorizontalAlignment = HorizontalAlignment.Center,
        };
        _messageLabel.AddThemeFontSizeOverride("font_size", 18);
        box.AddChild(_messageLabel, forceReadableName: false, InternalMode.Disabled);

        _progressBar = new ProgressBar
        {
            MinValue = 0,
            MaxValue = 100,
            Value = 0,
            CustomMinimumSize = new Vector2(0, 22),
        };
        box.AddChild(_progressBar, forceReadableName: false, InternalMode.Disabled);

        _detailLabel = new Label
        {
            Text = "",
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        _detailLabel.AddThemeFontSizeOverride("font_size", 15);
        box.AddChild(_detailLabel, forceReadableName: false, InternalMode.Disabled);

        _confirmButton = new Button
        {
            Text = "确定 / OK",
            Visible = false,
            CustomMinimumSize = new Vector2(0, 44),
        };
        _confirmButton.Pressed += OnConfirmPressed;
        box.AddChild(_confirmButton, forceReadableName: false, InternalMode.Disabled);
    }

    private async void RunWarmup()
    {
        try
        {
            if (ShaderWarmupMarker.IsCurrent(out string reason))
            {
                ShowResult(
                    ShaderWarmupResult.Skipped(
                        "着色器缓存已经可用。",
                        $"Shader cache is already current. release={ShaderWarmupMarker.ReleaseVersion}"
                    )
                );
                return;
            }

            PatchHelper.Log($"Shader warmup started; reason={reason}");
            ShaderPatchLoader.ApplyAll();

            Stopwatch stopwatch = Stopwatch.StartNew();
            SetProgress("正在扫描着色器资源...", 0f, "Scanning shader resources...");
            await ToSignal(RenderingServer.Singleton, RenderingServer.SignalName.FramePostDraw);

            List<(string Path, Material Material)> materials = await CollectMaterialsAsync();
            SetProgress("正在编译着色器...", 0.5f, $"Compiling {materials.Count} material(s)...");

            if (materials.Count == 0)
            {
                var emptyResult = new ShaderWarmupResult(
                    Success: true,
                    TotalMaterials: 0,
                    CompiledMaterials: 0,
                    FailedMaterials: 0,
                    ElapsedMilliseconds: stopwatch.ElapsedMilliseconds,
                    Message: "没有发现需要预热的着色器。",
                    Detail: $"No shader materials were found. release={ShaderWarmupMarker.ReleaseVersion}"
                );
                ShaderWarmupMarker.WriteSuccess(emptyResult);
                ShowResult(emptyResult);
                return;
            }

            var viewport = new SubViewport
            {
                Size = new Vector2I(64, 64),
                RenderTargetUpdateMode = SubViewport.UpdateMode.Always,
                TransparentBg = true,
            };
            AddChild(viewport, forceReadableName: false, InternalMode.Disabled);

            Image image = Image.CreateEmpty(1, 1, useMipmaps: false, Image.Format.Rgba8);
            image.SetPixel(0, 0, Colors.White);
            ImageTexture whiteTexture = ImageTexture.CreateFromImage(image);

            int failures = 0;
            for (int index = 0; index < materials.Count; index += BatchSize)
            {
                var batchNodes = new List<Node>();
                int end = Math.Min(index + BatchSize, materials.Count);
                for (int itemIndex = index; itemIndex < end; itemIndex++)
                {
                    var (path, material) = materials[itemIndex];
                    try
                    {
                        Node node = CreateWarmupNode(material, whiteTexture);
                        viewport.AddChild(node, forceReadableName: false, InternalMode.Disabled);
                        batchNodes.Add(node);
                    }
                    catch (Exception ex)
                    {
                        failures++;
                        PatchHelper.Log($"Shader warmup failed to create node for {path}: {ex.Message}");
                    }
                }

                float progress = 0.5f + (float)end / materials.Count * 0.5f;
                SetProgress(
                    $"正在编译着色器 ({end} / {materials.Count})...",
                    progress,
                    $"Compiling shaders ({end} / {materials.Count})..."
                );

                await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);
                await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);

                foreach (Node node in batchNodes)
                    node.QueueFree();
            }

            viewport.QueueFree();
            stopwatch.Stop();

            var result = new ShaderWarmupResult(
                Success: true,
                TotalMaterials: materials.Count,
                CompiledMaterials: Math.Max(0, materials.Count - failures),
                FailedMaterials: failures,
                ElapsedMilliseconds: stopwatch.ElapsedMilliseconds,
                Message: "着色器预热完成。",
                Detail: $"Compiled {Math.Max(0, materials.Count - failures)} / {materials.Count}, failed {failures}, release={ShaderWarmupMarker.ReleaseVersion}"
            );
            ShaderWarmupMarker.WriteSuccess(result);
            ShowResult(result);
            PatchHelper.Log(
                $"Shader warmup complete: total={result.TotalMaterials}, failed={result.FailedMaterials}, elapsedMs={result.ElapsedMilliseconds}"
            );
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup failed: {ex}");
            ShowResult(
                new ShaderWarmupResult(
                    Success: false,
                    TotalMaterials: 0,
                    CompiledMaterials: 0,
                    FailedMaterials: 0,
                    ElapsedMilliseconds: 0,
                    Message: "着色器预热失败。",
                    Detail: ex.Message
                )
            );
        }
    }

    private async Task<List<(string Path, Material Material)>> CollectMaterialsAsync()
    {
        var materials = new Dictionary<string, Material>();
        MaterialCollector.CollectFromDirectory("res://", materials);
        SetProgress(
            $"发现 {materials.Count} 个独立资源材质...",
            0.1f,
            $"Found {materials.Count} loose material resource(s)..."
        );
        await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);

        var scenePaths = new List<string>();
        MaterialCollector.CollectScenePaths("res://scenes", scenePaths);
        PatchHelper.Log($"Shader warmup found {scenePaths.Count} scene(s) to scan");

        var inFlight = new Queue<string>();
        int nextToQueue = 0;
        int completed = 0;
        int total = scenePaths.Count;
        while (completed < total)
        {
            while (inFlight.Count < BatchSize && nextToQueue < total)
            {
                string path = scenePaths[nextToQueue++];
                ResourceLoader.LoadThreadedRequest(
                    path,
                    "PackedScene",
                    useSubThreads: false,
                    ResourceLoader.CacheMode.Reuse
                );
                inFlight.Enqueue(path);
            }

            var pending = new Queue<string>();
            while (inFlight.Count > 0)
            {
                string path = inFlight.Dequeue();
                switch (ResourceLoader.LoadThreadedGetStatus(path))
                {
                    case ResourceLoader.ThreadLoadStatus.Loaded:
                        try
                        {
                            if (ResourceLoader.LoadThreadedGet(path) is PackedScene packed)
                                MaterialCollector.ExtractMaterialsFromSceneState(
                                    packed,
                                    path,
                                    materials
                                );
                        }
                        catch (Exception ex)
                        {
                            PatchHelper.Log($"Shader warmup skipped scene {path}: {ex.Message}");
                        }

                        completed++;
                        break;
                    case ResourceLoader.ThreadLoadStatus.Failed:
                        PatchHelper.Log($"Shader warmup scene load failed: {path}");
                        completed++;
                        break;
                    default:
                        pending.Enqueue(path);
                        break;
                }
            }

            inFlight = pending;
            float progress = total == 0 ? 0.5f : (float)completed / total * 0.5f;
            SetProgress(
                $"正在扫描场景 ({completed} / {total})...",
                progress,
                $"Scanning scenes ({completed} / {total})..."
            );
            await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);
        }

        var uniqueMaterials = new Dictionary<string, (string Path, Material Material)>();
        foreach (var entry in materials)
        {
            string shaderKey = MaterialCollector.GetShaderKey(entry.Value);
            uniqueMaterials.TryAdd(shaderKey, (entry.Key, entry.Value));
        }

        PatchHelper.Log(
            $"Shader warmup collected {materials.Count} material(s), {uniqueMaterials.Count} unique shader key(s)"
        );
        return uniqueMaterials.Values.ToList();
    }

    private static Node CreateWarmupNode(Material material, ImageTexture whiteTexture)
    {
        if (material is ParticleProcessMaterial particleMaterial)
        {
            return new GpuParticles2D
            {
                ProcessMaterial = particleMaterial,
                Amount = 1,
                Emitting = true,
                OneShot = false,
                Texture = whiteTexture,
            };
        }

        return new Sprite2D
        {
            Texture = whiteTexture,
            Material = material,
        };
    }

    private void SetProgress(string message, float fraction, string detail)
    {
        if (_messageLabel != null)
            _messageLabel.Text = message;
        if (_detailLabel != null)
            _detailLabel.Text = detail;
        if (_progressBar != null)
            _progressBar.Value = Math.Clamp(fraction, 0f, 1f) * 100f;
    }

    private void ShowResult(ShaderWarmupResult result)
    {
        _titleLabel.Text = result.Success
            ? "预热完成 / Warmup Complete"
            : "预热失败 / Warmup Failed";
        _messageLabel.Text = result.Message;
        _detailLabel.Text =
            $"{result.Detail}\n"
            + $"Total: {result.TotalMaterials}, Compiled: {result.CompiledMaterials}, Failed: {result.FailedMaterials}, Elapsed: {result.ElapsedMilliseconds}ms";
        _progressBar.Value = result.Success ? 100 : 0;
        _confirmButton.Visible = true;
        _confirmButton.GrabFocus();
    }

    private void OnConfirmPressed()
    {
        _completion?.TrySetResult(true);
        ReturnToLauncher();
    }

    private static void ReturnToLauncher()
    {
        try
        {
            GodotObject javaClassWrapper = Engine.GetSingleton("JavaClassWrapper");
            if (javaClassWrapper == null)
            {
                PatchHelper.Log("Shader warmup could not find JavaClassWrapper");
                return;
            }

            var wrapper = (GodotObject)
                javaClassWrapper.Call(
                    "wrap",
                    "com.dreamyloong.tlauncher.launch.AndroidExtensionHostActivity"
                );
            var activity = (GodotObject)wrapper.Call("getInstance");
            if (activity == null)
            {
                PatchHelper.Log("Shader warmup could not find AndroidExtensionHostActivity");
                return;
            }

            activity.Call("closeHostedRuntimeAndReturnToLauncher");
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup return-to-launcher failed: {ex.Message}");
        }
    }
}

public sealed record ShaderWarmupResult(
    bool Success,
    int TotalMaterials,
    int CompiledMaterials,
    int FailedMaterials,
    long ElapsedMilliseconds,
    string Message,
    string Detail
)
{
    public static ShaderWarmupResult Skipped(string message, string detail)
    {
        return new ShaderWarmupResult(
            Success: true,
            TotalMaterials: 0,
            CompiledMaterials: 0,
            FailedMaterials: 0,
            ElapsedMilliseconds: 0,
            Message: message,
            Detail: detail
        );
    }
}
