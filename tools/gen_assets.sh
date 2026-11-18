#!/bin/bash
set -e
R=src/main/resources
M=$R/assets/femboycompanion/models/item
D=$R/data/femboycompanion/recipes
mkdir -p "$M" "$D"

for n in white_femboy_spawn_egg black_femboy_spawn_egg; do
  echo '{"parent":"item/template_spawn_egg"}' > "$M/$n.json"
done

for n in white_ears white_double_ears white_tail white_paws black_ears black_double_ears black_tail black_paws; do
  echo "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"femboycompanion:item/$n\"}}" > "$M/$n.json"
done

for c in white black; do
  echo "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"W L\",\" W \"],\"key\":{\"W\":{\"item\":\"minecraft:${c}_wool\"},\"L\":{\"item\":\"minecraft:lead\"}},\"result\":{\"item\":\"femboycompanion:${c}_tail\",\"count\":1}}" > "$D/${c}_tail.json"
  echo "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\" W \",\"WPW\"],\"key\":{\"W\":{\"item\":\"minecraft:${c}_wool\"},\"P\":{\"item\":\"minecraft:pink_wool\"}},\"result\":{\"item\":\"femboycompanion:${c}_ears\",\"count\":1}}" > "$D/${c}_ears.json"
  echo "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\" W \",\"WBW\"],\"key\":{\"W\":{\"item\":\"minecraft:${c}_wool\"},\"B\":{\"item\":\"minecraft:leather_boots\"}},\"result\":{\"item\":\"femboycompanion:${c}_paws\",\"count\":1}}" > "$D/${c}_paws.json"
  echo "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"femboycompanion:${c}_ears\"},{\"item\":\"femboycompanion:${c}_ears\"}],\"result\":{\"item\":\"femboycompanion:${c}_double_ears\",\"count\":1}}" > "$D/${c}_double_ears.json"
done

python3 tools/gen_items.py
python3 tools/gen_textures.py
