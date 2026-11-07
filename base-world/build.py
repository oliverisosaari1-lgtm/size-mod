"""Writes commands.txt: builds Iikka's cross-shaped underground base at 0,0.
Grass top is y=0 (players walk at y=1). Floor y=-5, rooms y=-4..0, rim y=1."""
import random
random.seed(7)
C = []
def cmd(s): C.append(s)

A, W = 10, 3   # arm reach and half width of every room
inside = set()
for x in range(-A, A + 1):
    for z in range(-W, W + 1):
        inside.add((x, z)); inside.add((z, x))

def dist(x, z):
    return min(max(abs(x - a), abs(z - b)) for a, b in inside)

walls = {}
for x in range(-A - 2, A + 3):
    for z in range(-A - 2, A + 3):
        if (x, z) in inside: continue
        d = dist(x, z)
        if d <= 2: walls[(x, z)] = d

cmd("time set 1000")
cmd("weather clear 999999")
cmd("gamerule spawn_radius 0")
cmd("gamerule spawnRadius 0")
# dig the room space and lay the wooden floor
for (x, z) in inside:
    pass
cmd(f"fill {-A} -4 {-W} {A} 1 {W} air")
cmd(f"fill {-W} -4 {-A} {W} 1 {A} air")
cmd(f"fill {-A} -5 {-W} {A} -5 {W} spruce_planks")
cmd(f"fill {-W} -5 {-A} {W} -5 {A} spruce_planks")
cmd(f"fill {-W} -5 {-W} {W} -5 {W} oak_planks")
# walls: white quartz inside, dark blackstone outside, dark rim on top
for (x, z), d in walls.items():
    if d == 1:
        cmd(f"fill {x} -5 {z} {x} 0 {z} quartz_bricks")
    else:
        cmd(f"fill {x} -5 {z} {x} 0 {z} polished_blackstone_bricks")
    cmd(f"setblock {x} 1 {z} polished_blackstone")
# white corner caps (arm ends and the inner corners)
caps = []
for s in (-1, 1):
    for t in (-1, 1):
        caps += [((A + 1) * s, (W + 1) * t), ((W + 1) * t, (A + 1) * s), ((W + 1) * s, (W + 1) * t)]
for (cx, cz) in caps:
    for dx in (0, 1 if cx > 0 else -1):
        for dz in (0, 1 if cz > 0 else -1):
            cmd(f"fill {cx+dx} 1 {cz+dz} {cx+dx} 2 {cz+dz} smooth_quartz")
# partition walls with doorways between the hub and the three rooms
for (x1, z1, x2, z2) in ((-W - 1, -W, -W - 1, W), (W + 1, -W, W + 1, W), (-W, -W - 1, W, -W - 1)):
    cmd(f"fill {x1} -4 {z1} {x2} 0 {z2} quartz_bricks")
    cmd(f"fill {x1} 1 {z1} {x2} 1 {z2} polished_blackstone")
cmd(f"fill {-W-1} -4 -1 {-W-1} -2 1 air")
cmd(f"fill {W+1} -4 -1 {W+1} -2 1 air")
cmd(f"fill -1 -4 {-W-1} 1 -2 {-W-1} air")

# ---- south: entrance stairs from the grass down to the hub ----
for i, z in enumerate(range(A, W + 2, -1)):   # z = 10..6
    y = -i
    cmd(f"fill {-W} -5 {z} {W} {y-1} {z} spruce_planks")
    cmd(f"fill {-W} {y} {z} {W} {y} {z} spruce_stairs[facing=north]")
for z in (A + 1, A + 2):
    cmd(f"fill {-W} -5 {z} {W} 0 {z} smooth_quartz")
    cmd(f"fill {-W} 1 {z} {W} 1 {z} air")

# ---- hub: red carpet + storage chests ----
cmd("fill -1 -4 -1 1 -4 1 red_carpet")
for (x, z, f) in ((-W, -W, 'south'), (-W + 1, -W, 'south'), (W - 1, -W, 'south'), (W, -W, 'south')):
    cmd(f"setblock {x} -4 {z} chest[facing={f}]")
for (x, z) in ((-W, W), (W, W)):
    cmd(f"setblock {x} -4 {z} barrel[facing=up]")
for (x, z) in ((-W, -W + 1), (W, -W + 1)):
    cmd(f"setblock {x} -4 {z} lantern")

# ---- west: bedroom ----
cmd(f"setblock {-A} -4 0 red_bed[facing=west,part=head]")
cmd(f"setblock {-A+1} -4 0 red_bed[facing=west,part=foot]")
cmd(f"setblock {-A} -4 -1 red_bed[facing=west,part=head]")
cmd(f"setblock {-A+1} -4 -1 red_bed[facing=west,part=foot]")
cmd(f"fill {-A+2} -4 -1 {-A+2} -4 0 spruce_trapdoor[half=top,open=false]")
cmd(f"setblock {-A} -4 1 spruce_trapdoor[half=top]")
cmd(f"setblock {-A} -4 -2 spruce_trapdoor[half=top]")
for z in (-W, W):
    cmd(f"setblock {-A} -4 {z} potted_azalea_bush")
    cmd(f"fill {-A} -3 {z} {-A} -2 {z} azalea_leaves[persistent=true]")
cmd(f"setblock {-A+4} -4 {W} lantern")
cmd(f'summon painting {-A} -2 0 {{facing:5b,variant:"minecraft:wanderer"}}')

# ---- north: nether portal room ----
cmd(f"fill {-W} -4 {-A} {W} 0 {-A} obsidian")
cmd(f"fill -2 1 {-A} 2 1 {-A} obsidian")
cmd(f"fill -1 -2 {-A} 1 0 {-A} nether_portal[axis=x]")
cmd(f"fill -2 -4 {-A+1} 2 -4 {-A+1} spruce_planks")
cmd(f"fill -2 -3 {-A+1} 2 -3 {-A+1} spruce_stairs[facing=south]")
cmd(f"fill -2 -4 {-A+2} 2 -4 {-A+2} spruce_stairs[facing=south]")
cmd(f"setblock {W} -4 {-A+1} potted_azalea_bush")
cmd(f"fill {W} -3 {-A+1} {W} -2 {-A+1} flowering_azalea_leaves[persistent=true]")
cmd(f"setblock {-W} -4 {-A+3} lantern")

# ---- east: enchanting + crafting ----
cmd(f"setblock 7 -4 0 enchanting_table")
cmd(f"fill 6 -4 -2 8 -3 -2 bookshelf")
cmd(f"fill 6 -4 2 8 -3 2 bookshelf")
cmd(f"fill 9 -4 -1 9 -3 1 bookshelf")
for (z, b) in ((-3, 'crafting_table'), (-2, 'furnace[facing=west]'), (2, 'anvil[facing=north]'), (3, 'smithing_table')):
    cmd(f"setblock {A} -4 {z} {b}")
cmd(f"setblock 5 -4 {W} potted_azalea_bush")
cmd(f"fill 5 -3 {W} 5 -2 {W} azalea_leaves[persistent=true]")
cmd(f"setblock 5 -4 {-W} lantern")

# ---- grass and flowers around ----
plants = ['short_grass'] * 8 + ['dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet']
for _ in range(500):
    x, z = random.randint(-40, 40), random.randint(-40, 40)
    if dist(x, z) <= 3: continue
    cmd(f"setblock {x} 1 {z} {random.choice(plants)} keep")

cmd("setworldspawn 0 1 18")
open("commands.txt", "w").write("\n".join(C) + "\n")
print(len(C), "commands")
