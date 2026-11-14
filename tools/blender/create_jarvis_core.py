import bpy
import math
import random
import sys
from mathutils import Vector

# Jarvis Core — procedural Blender source.
# Run: blender --background --python tools/blender/create_jarvis_core.py -- --output assets/3d/jarvis_core.glb

random.seed(732)

def output_path():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if "--output" in args:
        return args[args.index("--output") + 1]
    return "assets/3d/jarvis_core.glb"

# Clean scene
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
for datablocks in (bpy.data.materials, bpy.data.curves, bpy.data.meshes):
    for block in list(datablocks):
        if block.users == 0:
            datablocks.remove(block)

root = bpy.data.objects.new("JarvisCore_Root", None)
bpy.context.collection.objects.link(root)

def make_energy_material(name, color_a, color_b, strength):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*color_b, 1.0)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    principled = nodes.new("ShaderNodeBsdfPrincipled")
    principled.inputs["Metallic"].default_value = 0.38
    principled.inputs["Roughness"].default_value = 0.28
    if "Emission Color" in principled.inputs:
        principled.inputs["Emission Color"].default_value = (*color_b, 1.0)
        principled.inputs["Emission Strength"].default_value = strength
    else:
        principled.inputs["Emission"].default_value = (*color_b, 1.0)
        principled.inputs["Emission Strength"].default_value = strength

    tex = nodes.new("ShaderNodeTexNoise")
    tex.inputs["Scale"].default_value = 3.7
    tex.inputs["Detail"].default_value = 7.0
    tex.inputs["Roughness"].default_value = 0.72

    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.22
    ramp.color_ramp.elements[0].color = (*color_a, 1.0)
    ramp.color_ramp.elements[1].position = 0.78
    ramp.color_ramp.elements[1].color = (*color_b, 1.0)

    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.18
    bump.inputs["Distance"].default_value = 0.045

    links.new(tex.outputs["Fac"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], principled.inputs["Base Color"])
    links.new(ramp.outputs["Color"], principled.inputs.get("Emission Color") or principled.inputs.get("Emission"))
    links.new(tex.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], principled.inputs["Normal"])
    links.new(principled.outputs["BSDF"], out.inputs["Surface"])
    return mat

core_mat = make_energy_material(
    "Jarvis_Energy_Cyan",
    (0.003, 0.025, 0.09),
    (0.015, 0.55, 1.0),
    2.8
)
plasma_mat = make_energy_material(
    "Jarvis_Plasma_Ice",
    (0.005, 0.12, 0.32),
    (0.28, 0.88, 1.0),
    4.5
)

# Main volumetric-looking sphere: real mesh with procedural surface shading.
bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=64, radius=1.0)
core = bpy.context.object
core.name = "Jarvis_Energy_Core"
core.data.materials.append(core_mat)
for face in core.data.polygons:
    face.use_smooth = True
core.parent = root

# A restrained inner highlight, not an orbital ring.
bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=32, radius=0.965)
inner = bpy.context.object
inner.name = "Jarvis_Core_Inner_Glow"
inner.data.materials.append(plasma_mat)
for face in inner.data.polygons:
    face.use_smooth = True
inner.parent = root

# Small suspended sparks, distributed in a loose 3D cloud around the core.
spark_mat = bpy.data.materials.new("Jarvis_Sparks")
spark_mat.diffuse_color = (0.12, 0.72, 1.0, 1.0)
spark_mat.use_nodes = True
spark_bsdf = spark_mat.node_tree.nodes.get("Principled BSDF")
if spark_bsdf:
    if "Emission Color" in spark_bsdf.inputs:
        spark_bsdf.inputs["Emission Color"].default_value = (0.08, 0.55, 1.0, 1.0)
        spark_bsdf.inputs["Emission Strength"].default_value = 3.0
    else:
        spark_bsdf.inputs["Emission"].default_value = (0.08, 0.55, 1.0, 1.0)
        spark_bsdf.inputs["Emission Strength"].default_value = 3.0
    spark_bsdf.inputs["Metallic"].default_value = 0.2
    spark_bsdf.inputs["Roughness"].default_value = 0.3

for i in range(42):
    direction = Vector((
        random.uniform(-1.0, 1.0),
        random.uniform(-1.0, 1.0),
        random.uniform(-1.0, 1.0)
    )).normalized()
    distance = random.uniform(1.08, 1.42)
    radius = random.uniform(0.009, 0.025)
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=radius, location=direction * distance)
    spark = bpy.context.object
    spark.name = f"Jarvis_Spark_{i:02d}"
    spark.data.materials.append(spark_mat)
    spark.parent = root

# Curved, broken plasma filaments create organic motion cues without closed orbit rings.
for strand_index in range(5):
    curve = bpy.data.curves.new(f"Jarvis_Plasma_Filament_{strand_index:02d}", "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 16
    curve.bevel_depth = 0.006
    curve.bevel_resolution = 3
    spline = curve.splines.new("POLY")
    point_count = 34
    spline.points.add(point_count - 1)
    phase = strand_index * (math.tau / 5.0)
    for point_index in range(point_count):
        t = point_index / (point_count - 1)
        angle = phase + t * 2.4 + math.sin(t * 9.0 + phase) * 0.16
        latitude = -0.9 + t * 1.8 + math.sin(t * 13.0 + phase) * 0.12
        radius = 1.015 + math.sin(t * 17.0 + phase) * 0.035
        x = radius * math.cos(latitude) * math.cos(angle)
        y = radius * math.cos(latitude) * math.sin(angle)
        z = radius * math.sin(latitude)
        spline.points[point_index].co = (x, y, z, 1.0)
    obj = bpy.data.objects.new(curve.name, curve)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(plasma_mat)
    obj.parent = root

# Keep the root selected so export includes only the Jarvis asset.
bpy.ops.object.select_all(action="DESELECT")
for obj in bpy.context.scene.objects:
    if obj == root or obj.parent == root:
        obj.select_set(True)
bpy.context.view_layer.objects.active = root

path = output_path()
bpy.ops.export_scene.gltf(
    filepath=path,
    export_format="GLB",
    use_selection=True,
    export_apply=True
)
print(f"Jarvis Core exported: {path}")
