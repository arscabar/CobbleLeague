# CobbleSynergy source

Upstream: https://github.com/JaekunE2/CobbleSynergy
Integrated base commit: cf19c86 (upstream 0.3.7), merged with the 0.4.1 compatibility changes
License declared by upstream fabric.mod.json: MIT.

The 0.4.1 source is maintained in both repositories with exact CobbleLeague 0.8.1 metadata. It includes the upstream operator-controlled AI trainer, improved role/move/switch/item planning and normal/steel default improvements, the delegated spectator lifecycle, and timed Fairy accuracy effects. Migration from design 3/4 updates Fairy only and preserves other user settings.

Build after the core: `./gradlew -p server-addons/cobble-synergy -Pcobbleleague_jar=../../build/libs/cobbleleague-0.8.1.jar test build`
