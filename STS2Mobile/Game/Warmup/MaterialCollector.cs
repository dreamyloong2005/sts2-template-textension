using System;
using System.Collections.Generic;
using Godot;

namespace STS2Mobile.Game.Warmup;

internal static class MaterialCollector
{
    public static string GetShaderKey(Material material)
    {
        if (material is ShaderMaterial { Shader: not null } shaderMaterial)
        {
            string resourcePath = shaderMaterial.Shader.ResourcePath;
            return string.IsNullOrWhiteSpace(resourcePath)
                ? shaderMaterial.Shader.GetRid().ToString()
                : resourcePath;
        }

        if (material is ParticleProcessMaterial)
            return $"particle#{material.GetRid()}";

        return string.IsNullOrWhiteSpace(material.ResourcePath)
            ? material.GetRid().ToString()
            : material.ResourcePath;
    }

    public static void CollectFromDirectory(
        string dirPath,
        Dictionary<string, Material> materials
    )
    {
        try
        {
            using DirAccess dir = DirAccess.Open(dirPath);
            if (dir == null)
                return;

            dir.ListDirBegin();
            string next;
            while ((next = dir.GetNext()) != "")
            {
                if (next == "." || next == "..")
                    continue;

                string resourcePath = $"{dirPath}/{next}";
                if (dir.CurrentIsDir())
                {
                    if (next != "debug")
                        CollectFromDirectory(resourcePath, materials);
                    continue;
                }

                string fileName = next.Replace(".remap", "");
                if (
                    !fileName.EndsWith(".tres", StringComparison.Ordinal)
                    && !fileName.EndsWith(".gdshader", StringComparison.Ordinal)
                    && !fileName.EndsWith(".material", StringComparison.Ordinal)
                )
                {
                    continue;
                }

                string normalizedPath = $"{dirPath}/{fileName}";
                if (materials.ContainsKey(normalizedPath) || !ResourceLoader.Exists(normalizedPath))
                    continue;

                try
                {
                    if (fileName.EndsWith(".tres", StringComparison.Ordinal))
                    {
                        if (
                            ResourceLoader.Load(
                                normalizedPath,
                                "Material",
                                ResourceLoader.CacheMode.Reuse
                            ) is Material material
                        )
                        {
                            materials[normalizedPath] = material;
                        }
                        else if (
                            ResourceLoader.Load(
                                normalizedPath,
                                "Shader",
                                ResourceLoader.CacheMode.Reuse
                            ) is Shader shader
                        )
                        {
                            materials[normalizedPath] = new ShaderMaterial { Shader = shader };
                        }
                    }
                    else
                    {
                        Resource resource = ResourceLoader.Load(
                            normalizedPath,
                            null,
                            ResourceLoader.CacheMode.Reuse
                        );
                        if (resource is Material material)
                            materials[normalizedPath] = material;
                        else if (resource is Shader shader)
                            materials[normalizedPath] = new ShaderMaterial { Shader = shader };
                    }
                }
                catch (Exception ex)
                {
                    PatchHelper.Log($"Shader warmup failed to load {normalizedPath}: {ex.Message}");
                }
            }

            dir.ListDirEnd();
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup failed to enumerate {dirPath}: {ex.Message}");
        }
    }

    public static void CollectScenePaths(string dirPath, List<string> paths)
    {
        try
        {
            using DirAccess dir = DirAccess.Open(dirPath);
            if (dir == null)
                return;

            dir.ListDirBegin();
            string next;
            while ((next = dir.GetNext()) != "")
            {
                if (next == "." || next == "..")
                    continue;

                string resourcePath = $"{dirPath}/{next}";
                if (dir.CurrentIsDir())
                {
                    if (next != "debug")
                        CollectScenePaths(resourcePath, paths);
                    continue;
                }

                string fileName = next.Replace(".remap", "");
                if (!fileName.EndsWith(".tscn", StringComparison.Ordinal))
                    continue;

                string normalizedPath = $"{dirPath}/{fileName}";
                if (ResourceLoader.Exists(normalizedPath))
                    paths.Add(normalizedPath);
            }

            dir.ListDirEnd();
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup failed to enumerate scenes in {dirPath}: {ex.Message}");
        }
    }

    public static void ExtractMaterialsFromSceneState(
        PackedScene packed,
        string scenePath,
        Dictionary<string, Material> materials
    )
    {
        SceneState state = packed.GetState();
        int nodeCount = state.GetNodeCount();
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++)
        {
            int propertyCount = state.GetNodePropertyCount(nodeIndex);
            for (int propertyIndex = 0; propertyIndex < propertyCount; propertyIndex++)
            {
                string propertyName = state.GetNodePropertyName(nodeIndex, propertyIndex).ToString();
                if (
                    propertyName != "material"
                    && propertyName != "process_material"
                    && propertyName != "surface_material_override/0"
                )
                {
                    continue;
                }

                try
                {
                    Variant value = state.GetNodePropertyValue(nodeIndex, propertyIndex);
                    string key = $"{scenePath}#node{nodeIndex}#{propertyName}";
                    if (value.Obj is Material material)
                    {
                        materials.TryAdd(key, material);
                    }
                    else if (value.Obj is Shader shader)
                    {
                        materials.TryAdd(key, new ShaderMaterial { Shader = shader });
                    }
                }
                catch (Exception ex)
                {
                    PatchHelper.Log(
                        $"Shader warmup failed to read {propertyName} in {scenePath}: {ex.Message}"
                    );
                }
            }
        }
    }
}
