# CobbleSynergy source

Upstream: https://github.com/JaekunE2/CobbleSynergy
Imported base commit: ad4fb3afdc10aa785ff6b23d5cacb046b8ea7ed6
License declared by upstream fabric.mod.json: MIT.

This copy integrates the spectator lifecycle with CobbleLeague 0.7.4 and changes Fairy synergy to a timed accuracy shield. Other synergy defaults remain unchanged. The original received folder remains the separate upstream working copy.

Build from the repository root after the core: `./gradlew -p server-addons/cobble-synergy -Pcobbleleague_jar=../../build/libs/cobbleleague-0.7.4.jar test build`
