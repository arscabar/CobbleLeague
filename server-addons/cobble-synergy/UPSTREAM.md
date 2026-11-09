# CobbleSynergy source

Upstream: https://github.com/JaekunE2/CobbleSynergy
Integrated base commit: c29aa19441ee9427fedf213e5206f2ff12ca1382
License declared by upstream fabric.mod.json: MIT.

The 0.4.0 source is maintained in both repositories with exact CobbleLeague 0.8.0 metadata. It includes the upstream test-only AI trainer and normal/steel default improvements, the delegated spectator lifecycle, and timed Fairy accuracy effects. Migration from design 3/4 updates Fairy only and preserves other user settings.

Build after the core: `./gradlew -p server-addons/cobble-synergy -Pcobbleleague_jar=../../build/libs/cobbleleague-0.8.0.jar test build`
