# Changelog

## [June 24, 2026] - High Seas Architecture, Sails, Boat Propulsion & Submarine Extensions

### Sail & Wind Engine (Create: High Seas)
- **Wind System & Manager:** Integrated a dynamic wind simulation engine (`WindManager`, `WindConfig`) with spatial wind direction/speed sampling (`SailWindSystem`, `SailWindRegistry`).
- **Sail Detection & Physics:** Implemented dynamic multi-segment sail detection (`SailDetector`), sail wind force distribution (`SailForce`), decaying sail dynamics (`DecayingSail`), and structural boat classification (`BoatClassifier`).
- **Dynamic Furling Sails:** Added full sail furling support (`SailFurlHandler`, `FurlSavedData`, `FurlSyncPayload`, `SailCollisionSystem`), allowing sails to be dynamically unfurled or stowed away with accurate collision boundaries.
- **Wind Vane & Goggles Integration:** Added the Wind Vane block, block entity (`WindVaneBlockEntity`), visual rendering (`WindVaneVisual`, `WindVaneRenderer`), client angle tracking (`WindVaneAngle`), and Create Goggle HUD overlay (`WindVaneGoggle`) to display real-time wind speed and bearing. Replaced the legacy Weather Vane block.
- **Custom Sail Tessellation Shader:** Added custom Pinwheel tessellation and vertex/fragment shaders (`sail.vsh`, `sail.fsh`, `sail.tcsh`, `sail.tesh`) for realistic cloth movement, dynamic wind waving, shader state management (`SailShaderState`), and custom render types (`SailRenderTypes`).
- **Iris & Sodium Pipeline Compatibility:** Implemented Iris shader phase mixins (`IrisCompat`, `IrisSailPhaseMixin`), contraption sail baking (`ContraptionSailBakeMixin`, `ContraptionVisualSailBakeMixin`), and `SailSodiumMaterialMixin` for seamless sail rendering under Iris and Sodium pipelines.
- **Reworked Sail Thrust:** Each sail now pushes along its own facing, on the side the wind actually hits, instead of blindly following the hull's keel. Thrust depends on how squarely the wind hits the canvas and on the point of sail, with a configurable floor so a ship can still claw its way upwind. Square hulls with no rudder take their heading from the sails. Acceleration now scales with hull mass, so a big ship needs more canvas than a dinghy. Furled sails give no thrust at all.
- **Sail Billow Follows the Wind:** The tessellated canvas now bulges toward the leeward side and flips when the wind swaps sides, and the furl animation plays smoothly in the shader when a sail is stowed or unfurled.
- **Wind Debug Arrow:** The F3+B sail arrow now points backwards when a sail is pulling the ship in reverse.
- **High Seas Creative Section:** All High Seas blocks and items are grouped under a new "High Seas" section of the Simulated creative tab.
- **New Sail Block:** High Seas now has its own Sail (`create_high_seas:white_sail`), a full copy of Simulated's Symmetric Sail: same thin shape, same drag on sub-levels, same bounce, same click-to-extend placement, sixteen colours by right-clicking with a dye, and it still sticks to its neighbours when a ship is assembled. It is the only block that catches the wind, billows, furls and gets the wind-side collision. Simulated's Symmetric Sails are back in their original place in the Simulated tab and behave exactly like they do without High Seas. Recipe: two Windmill Sails and a String give two Sails; a Sail can be turned back into a Windmill Sail. The Rudder recipe now uses the new Sail.
- **Heads-up:** Symmetric Sails already placed on existing boats no longer push the ship or billow. Replace them with the new Sail.

### High Seas Configuration
- **Dedicated Config File:** Create: High Seas now has its own configuration (`HighSeasConfig`), editable from the in-game config screen with full English and French descriptions:
  - **Wind:** base wind strength, gust amplitude, rain and thunderstorm boost, terrain shelter.
  - **Sails:** overall sail power, windless thrust, wind bonus, upwind efficiency floor, sail rescan interval.
  - **Boat Engine:** forward power and reverse power share.
  - **Oars:** stroke power and rowing top speed.
  - **Seaglide:** thrust and top speed.
  - **Buoy:** maximum rise and sink speed.
  - **Anchor:** holding mass once it bites the sea floor.
  - **Client:** sail mesh subdivision level (GPU tessellation) and seaglide screen effects (camera shake, speed lines).

### Boat Engines, Helm & Vehicle Mechanics (Create: High Seas)
- **Boat Engine System:** Introduced the Boat Engine block, item, and block entity (`BoatEngineBlockEntity`), container menu & GUI screen (`BoatEngineMenu`, `BoatEngineScreen`), 3D block entity renderer (`BoatEngineRenderer`), client throttle handler (`EngineThrottleHandler`), dynamic exhaust particle visuals (`BoatEngineExhaustVisual`), spatial engine audio (`BoatEngineSoundInstance`, `BoatEngineSoundHandler`), and network payloads (`EngineTogglePayload`, `EngineStatePayload`, `ThrottleCapPayload`).
- **Helm & Steering System:** Added Helm seat entity & renderer (`HelmSeatEntity`, `HelmSeatRenderer`), steering input dispatcher (`HelmClient`, `HelmServer`, `HelmInput`, `HelmInputPayload`, `HelmReleasePayload`), and custom player pose animations (`HelmPoseAnimator`, `PlayerPoseDispatcher`).
- **Rudders & Anchors:** Added Submarine/Ship Rudder block (`RudderBlock`, `RudderItem`), placement helper (`RudderPlacementHelper`), Copycat model disguises (`RudderCopycatModel`), structural rudder detection (`RudderDetector`), and Anchor blocks (`AnchorBlock`, `AnchorItem`, `AnchorBlockEntity`) for securing ships against currents and wind.
- **Engine Details:** The boat engine burns fuel from its own GUI (sneak + right-click), refuses to start when its propeller space is blocked, and plays separate idle and running sounds. Holding right-click at the helm and moving the mouse sets the speed limit from 0 to 15.
- **Anchor Behaviour:** An anchor weighs about 20 kg while hanging and pins the ship with a full ton (configurable) once it touches the bottom. It only accepts steel cables. Connecting one turns the anchor into its own sub-level hanging from the rope winch, and the steel cable attaches to the anchor's real ring instead of the block center. A winch whose anchor is grounded now sends a block update to its neighbours, and the signal clears itself once the cable is gone (`WinchAnchorSignal`).
- **Seats Stay Aboard:** Helm and buoy seats are tagged so Sable keeps them in their sub-level and removes them along with it.
- **Recipes & Loot:** Added crafting recipes for the Boat Engine, Buoy, Rudder, Seaglide and Wind Vane, plus loot tables for the new blocks. The Wind Vane and Rudder need an iron pickaxe.

### Rowing Mechanics, Seaglide & Buoys (Create: High Seas)
- **Rowing Propulsion Mechanics:** Added the Oar item (`OarItem`), physical rowing propulsion simulation (`OarPropulsionSystem`), synchronized client/server player rowing pose animations (`OarRowAnimator`, `OarClientHandler`), and networking (`OarRowPayload`, `OarAnimSyncPayload`).
- **Seaglide Underwater Vehicle:** Added the Seaglide handheld propulsion scooter item (`SeaglideItem`), attack safety guard (`SeaglideAttackGuard`), battery drain and collision impact payloads (`SeaglideDrainPayload`, `SeaglideImpactPayload`), custom first/third-person hand rendering (`SeaglideItemRenderer`), player pose animation (`SeaglidePoseAnimator`), bubble/water trail particle visual effects (`SeaglideRushEffects`), and spatial underwater propulsion audio (`SeaglideSoundInstance`).
- **Buoy Rings & Passenger Seating:** Added Buoy ring/seat blocks (`BuoyBlock`, `BuoyItem`, `BuoyBlockEntity`, `BuoySeatEntity`), custom passenger seating animations (`BuoyPoseAnimator`, `BuoySeatRenderer`), dynamic riding tilt effects (`BuoyRenderTilt`), and riding particle effects (`BuoyRideEffects`).
- **Buoy Particles:** Breaking, hitting, walking on or landing on a buoy now throws red and white wool bits, matching its colours.

### Hydrodynamic Buoyancy & Hold Water Rendering
- **Hydrodynamic Buoyancy Simulation:** Implemented `BoatBuoyancySystem`, `BoatManager`, and `BuoyFloatSystem` for calculating hydrodynamic floating, displacement, and torque forces on boat hulls, accompanied by debug renderers (`BoatBuoyancyDebugRenderer`, `BoatCullDebugRenderer`).
- **Dry Boat Holds:** Boats now use the same pixel-perfect water occlusion as submarines (`BoatManager` feeding the shared `CompartmentTracker`), which replaces the old block-by-block boat culling (`BoatWaterCulling`, `BoatCullBuffer` and `BoatCompartmentDetector` are gone). Water stays out of open holds that sit below the waterline. Boat water culling (`enableBoatWaterCulling`) is now on by default.
- **Compartment-by-Compartment Flooding:** When a hold floods, only that compartment loses its occlusion and its air. A neighbouring hold behind a bulkhead stays dry, and the buoyancy reads which holds are flooded, so a ship lists and settles instead of sinking outright.
- **Sunken Boats Flood:** A fully sealed boat that ends up underwater no longer stays a dry air bubble you can breathe in. Each hold looks at its own highest air cell: once the sea has covered it for two seconds, the hold counts as flooded, so its water culling turns off, the air is gone, and it stops giving lift, and the boat sinks. A short plunge (dropping a boat in, a big wave) is ignored.
- **No Double Ownership:** A sub-level already run by a submarine controller or oxygen diffuser is left alone by the boat system, so the two no longer write over each other for the same ship.

### Submarine Equipment & Diffuser Protection (Create: Deep Seas)
- **Submarine Staff (not in this release):** Added the Submarine Staff item (`SubmarineStaffItem`), featuring custom 3D OBJ modeling (`submarine_staff.obj`), dynamic crystal tier states (tiers 1-4), dynamic charging animations, particle emissions, light effects, custom sound triggers, server command hooks, and custom hand rendering (`SubmarineStaffItemRenderer`, `SubmarineStaffClientHandler`). It is still being worked on and is only available in development builds for now.
- **Oxygen Bucket:** Added the Oxygen Bucket item (`OxygenBucketItem`) for portable oxygen transfer and fluid handling.
- **Diffuser Zone Protection:** The space right above an Oxygen Diffuser or an Electrolyzer is now properly protected (`DiffuserZoneProtection`). Placing a block there by right-click, by any other means, or pushing one in with a piston is refused, with a "This spot is occupied by the machine" message.
- **Physics Wake System:** Added `PhysicsWakeSystem` to generate realistic water wake particles and hydrodynamic turbulence behind moving sublevel vessels.
- **Connected Ballast Tanks:** Ballast Tanks now connect when stacked, with top, middle, bottom and single models and connected textures (`BallastTankCT`), just like Create's fluid tanks.
- **Ballast Layer Placement:** Placing a Ballast Tank on the top or bottom face of another one copies the whole layer you clicked on (up to 64 tanks), like Create's fluid tanks. Sneak to place a single block.
- **Ballast Vent Rework:** The Ballast Vent is now just a sea valve. It no longer takes rotation, redstone or a rotation direction, and has no shaft anymore. Pipe it anywhere (all six faces connect): when a pump pulls from it, it supplies seawater, and when a pump pushes water into it, the water is dumped back into the sea. At least one open face must be touching the sea. Filling and draining the ballasts is now up to the pump in the pipe line.
- **Onboard Computer (WIP):** Added the Onboard Computer, a tilted paper chart-screen that shows the submarine's depth on a live scrolling gauge, with the surface line, the target depth and the hull's crush limit. The submarine itself is drawn in the middle as a live side-view blueprint, using the same line-drawing look as the Simulated diagram. There is no GUI: you aim at the buttons directly on the screen and left-click them. Breaking the computer now takes Sneak + break, so a stray click on the screen never knocks it off. The speed buttons (slow, cruise, fast) switch instantly; clicking the depth field locks your movement and lets you type the depth on the keyboard, then Sneak, Enter or Esc confirms it. It automatically finds every Pump Controller on the same submarine and steers the dive by itself: it works out the ballast level that gives the right climb or dive rate for the chosen speed, orders the pumps to fill, drain or hold, and slows down as it nears the target depth. It measures how fast your pumps can really change the ballast level and starts braking just early enough to stop on the target instead of overshooting, so more or faster pumps simply mean quicker trips. Within the last two blocks, virtual diving planes take over the fine adjustment and hold the depth exactly. The screen shows the ballast level and what the pumps are doing.
- **Pump Controller:** A new pump that works exactly like Create's Mechanical Pump (driven by a cogwheel, pipes on both ends, same pressure and range), with its own animated cog. Build it anywhere on a submarine that has an Onboard Computer and the computer takes over on its own, with no link or wiring needed: it decides whether the pump fills the ballasts, drains them or stops, and how fast. You don't need to worry about which way it faces: it follows its pipes, finds which end leads to the Ballast Tanks and which leads to the Ballast Vent, and always pumps the right way. Several Pump Controllers side by side or in a row all agree on the direction instead of one filling while the other drains. The wrench no longer flips it, and its cog sits slightly forward to line up with the casing. On a submarine without a computer it is a plain pump.
- **Big Submarine Propeller:** Place four Submarine Propellers in a 2x2 square, all facing the same way, and they merge into one propeller twice as wide, spinning around the middle of the square. It pushes with the combined thrust of the four (4x a single one) from the centre of the square, and each block still takes its own stress, so the torque needed is 4x as well. The square must stand on its own: a fifth propeller facing the same way right next to it cancels the merge. Breaking any of the four splits them back into single propellers.
- **Electrolyzer Alternator Module:** The Electrolyzer GUI now has a module slot (and shows your inventory). Put a Create Electron Tube in it and a shaft appears through the Electrolyzer's base. Spin it and the machine turns rotation into its own power: 4 FE/t per RPM (up to 1000 FE/t), so 64 RPM is enough to run it non-stop. It costs 8 SU per RPM while the module is fitted. The shaft always runs across the two electrode cables, front to back. Hover the energy gauge to see what the alternator is producing. Taking the tube out removes the shaft; breaking the Electrolyzer drops the tube. The Electrolyzer's Ponder scene now ends with a step showing the module and the shaft.
- **Onboard Computer Ponder:** The Onboard Computer and the Pump Controller share a new Ponder scene: the hull, computer, ballasts and pump line build up step by step, then the screen shows a full dive (filling, braking, holding the depth).
- **Computer & Pump Controller Recipes:** The Onboard Computer is crafted from a Transmitter surrounded by planks, with Crafter Slot Covers in the corners. The Pump Controller is crafted from a Mechanical Pump with a Transmitter below it and copper blocks around, leaving the two top corners empty. Both blocks now mine like copper and need at least a stone pickaxe.
- **Cheaper Underwater Mine:** The mine recipe no longer needs a lodestone.
- **Solid Mines & Pulleys:** Underwater Mines and Pulleys now have a full block collision box, so you no longer walk or fall through them. The pulley arm pivot and models were also realigned.

### Configuration Split (Create: Deep Seas)
- **Common / Server / Client Files:** The single Deep Seas config has been split in three. Forces, speeds, hull strength and gameplay rules now live in the **server** config, which NeoForge syncs to every connected player. Startup screens and other display options live in the **client** config. Only the world-generation options (Deeper Oceans) remain in the **common** config.
- **Heads-up:** Values you changed in the old common file for gameplay, hull strength or mechanics are not carried over. They start again from their defaults in the new per-world server config.
- **Per-World Depth Cap:** `globalMaxDepthCap` now belongs to the world and is applied again when the world loads, instead of being read before any world exists.

### Dependencies & Compatibility
- **Version Bump:** Create: Deep Seas is now **3.0.0** and Create: High Seas is **0.1.1**.
- **Create Aeronautics 1.3.2:** Now requires Create Aeronautics **1.3.2+** (which fixes the swivel bearing mass used by rudders) and Simulated **1.3.0+** (Simulated is now an explicit dependency).
- **Sable 2.0.3:** Moved to Sable **2.0.3** (with Sable Rapier) and Sable Companion **1.6.0**.
- **Veil 4.1.4 & Sodium 0.8:** Updated to Veil **4.1.4** and to Sodium **0.8.12 beta**.
- **Bundled Dependencies:** Lithostitched and Fusion (connected glass) are now shipped inside the mod jar, so no separate download is needed.
- **NeoForge:** Built against NeoForge 21.1.228.
- **High Seas Logo:** Create: High Seas now shows its own logo in the mods list.

### Removed
- **Weather Vane & Submarine Rudder:** The old `create_submarine:weather_vane` and `create_submarine:submarine_rudder` blocks have been removed and replaced by the Wind Vane and Rudder from Create: High Seas. Any of the old blocks already placed in a world will disappear.

### Bug Fixes & Refactoring
- **Sublevel Cable & Winch Physics:** Hardened rope winch and steel cable connections across sublevel boundaries (`RopeWinchBlockMixin`, `RopeDataSubLevelMixin`, `RopeTrackingSubLevelMixin`, `SubLevelCableCleanup`, `WinchAnchorSignal`), adding network payloads (`CableStrandRemovePayload`) for synced cable strand removal.
- **Water Occlusion & Fog Optimization:** Overhauled `SubmarineWaterCullBuffer`, `SodiumWaterOcclusionBridge`, and `FlywheelFogUniformsMixin` for improved performance and accurate fog density updates in sublevel air pockets (`SableSubLevelPocketFogMixin`).
- **Rockcutting Wheel Assets:** Updated high-resolution textures and 3D models (`spline_block.obj`, `spline_core.obj`, `spline_static.obj`) for the Rockcutting Wheel block.
- **No More Holes in the Sea:** Water occlusion now only hides cells the hull fully fills. Stairs, slabs and other partial blocks on a hull no longer cut a hole in the surrounding water.
- **Out-of-Water Ships:** A sub-level that isn't near any water, for example one flying or sitting on land, no longer applies water occlusion. Occlusion now refreshes as soon as a ship moves or its hull changes, instead of lagging behind.
- **Fog in Air Pockets:** Inside a sealed hull, fog is no longer switched off completely. It now starts just past the hull walls, so the ocean seen through windows keeps its fog. The pocket fog check also tests the exact cell in the ship's local space, and the fog colour is now uploaded correctly.
- **Sturdier Sodium Shader Injection:** The water-occlusion code is now injected after every preprocessor directive (`#extension`, `#define`, etc.) and at the end of `main()`, and it reads the depth buffers with `texelFetch`. This fixes shader compile errors with other mods and packs that edit Sodium's shaders, and occlusion that broke at non-native resolutions.
- **Steel Cable Fixes:**
  - Breaking a steel cable now shows steel cable particles instead of rope particles.
  - Fixed a rope reused after a steel cable was destroyed or detached still behaving as steel.
  - The cable collider is a bit thicker, so you're less likely to slip through it.
  - Cable pushes now move players with proper collision, so they can no longer be shoved into walls or pushed down into the floor.
- **Goggle Detection:** Goggle overlays now use Create's own check, so every goggle-compatible helmet works, not only items with "goggles" in their name.
- **Floater Performance:** A floater cluster now reads the ship's physics state once per tick and applies one combined impulse, and the water surface lookup is cached for two seconds. This makes large floater arrays much cheaper.
- **Oxygen Diffuser Scans:** Diffusers now rescan their compartment only when the hull actually changes, or every 10 seconds, instead of every second.
- **Air Doesn't Lift Submarines:** Air pumped into a submarine by an Oxygen Diffuser is now only for breathing and no longer floats the hull. Between two hull scans, the boat system could briefly take over a submarine and apply boat buoyancy to its air-filled compartments. The diffuser and hull controller now keep their claim on the ship every tick.
- **Physics Wake-Up:** Ships now actually wake up when a force is applied to them. The old sleep toggle did nothing on current Sable versions.
- **Early Config Crash:** Fixed hull-strength and compartment-scan code reading the server config before it was loaded, e.g. on the client before the server config synced.
- **Boat Classifier Crash:** A broken sub-level chain no longer crashes the sail system.
- **Invisible Cables on Anchors & Buoys:** A steel cable or rope started from an anchor or a buoy was fully simulated but never drawn, because only winches and rope connectors rendered the ropes they owned. Anchors and buoys now draw their own ropes too.
- **Anchor Fling:** Tying a steel cable to an anchor no longer flips or launches it (and the boat). The anchor was being dropped half a block into the hull right after becoming its own sub-level, and its arms reached into the neighbouring blocks. It now keeps the position, rotation and speed it had on the boat, and its collision box stays inside its own block.
- **Buoy Placement:** Buoys also keep their exact position when they turn into a floating sub-level, instead of sinking half a block first.
- **Helm Body Jitter:** Looking around while holding the tiller no longer makes the player's body twitch. The body now stays locked to the engine's heading and only the head turns.
- **Helm Seat in the Hull:** You can no longer take the tiller when there is no room to sit behind the engine, which used to put the player model inside the boat's blocks. A "There is no room to sit at the tiller" message explains why.
- **Faster Boat Engine:** Default engine power raised from 16 to 36 (about 1.5× the top speed), so engine boats now outrun vanilla boats. Existing configs keep their old value.
- **Rowing from a Buoy:** Rowing while sitting in a buoy now plays the rowing arm animation instead of freezing in the buoy pose.
- **Seaglide Size in Third Person:** The Seaglide is now 1.5× bigger in third person to match how it looks in first person.
- **Stuck on Steel Cables:** A steel cable running through a block (a winch, a floor) no longer makes you bob in place when you stand on that block. Cable collision now ignores the parts of the cable buried inside solid blocks, in the world or on a ship.
- **Floater Bobbing & Launching:** Floaters no longer bob endlessly, and a lone floater no longer shoots several blocks out of the water. A floater only weighs 0.1, so its push easily overshot. Each floater's push is now capped to what its share of the ship's mass can take in one tick, so it settles on the surface instead.
- **Pressure Without Water:** A submarine sitting in air, for example in a dry cave under the sea or under a lake, no longer takes water pressure from the water above it. Pressure now only counts an unbroken water column rising from the submarine itself: it stops at the first pocket of air, and the submarine has to actually be in water for any pressure to apply. Solid blocks such as rock or ice in the column don't cut it. The Onboard Computer's depth reading follows the same rule.
- **Submarines Rise Straight:** A submarine going up or down on its ballasts no longer tilts and slides off to the side. Sable's own water buoyancy works block by block, so an uneven hull used to pitch as it rose and then glide sideways like a wing. Submerged submarines now slowly right themselves back to level on pitch and roll, the way a real hull does when its buoyancy sits above its centre of gravity. Turning (yaw) is left completely free.
- **Submarines Follow Their Heading:** Turning a submarine used to spin the hull while it kept sliding in its old direction, because the water barely resisted sideways motion. Submarines with a clear long axis now get keel drag: sideways sliding is damped, so the submarine carves the turn and moves where its bow points. Square hulls, which have no obvious front, are left as before.
- **Steel Cables No Longer Sink You:** Walking into a steel cable while standing on the ground could push players, mobs and armor stands down into the blocks under them, and make them fall through a one-block platform. A cable never pushes a grounded entity downwards any more, and the player's cable collision on the client now respects block collisions instead of teleporting the player.
- **Sodium + Veil Crash:** Fixed a crash on start-up with Sodium when another Veil-based mod (such as Flares) also rewrites Sodium's block shaders. The water-occlusion patch is now inserted into the real `main()` function instead of before the last closing brace of the file, the unused fog patch is gone, and if Sodium still can't compile the patched shader, the game logs a warning and loads the original shader instead of crashing. In that case only the water occlusion inside ships is lost.
- **Oxygen Diffuser / Electrolyzer Dupe:** An Oxygen Diffuser or Electrolyzer can no longer be placed, or pushed by a piston, under a block that would end up inside its tall model, which could be used to duplicate that block and its contents. Trying to place a block on top of one of them also no longer makes the item vanish from your hand until the slot refreshes.
- **Floater & Ballast Performance:** Floaters and Ballast Tanks no longer look up their whole group on every physics step. The group (and which block leads it) is now worked out once, on the server tick, and only again when a floater or tank is placed, loaded or removed, with a safety refresh every 5 seconds. Every tank in a big ballast used to rebuild the full group on its own every 5 ticks; they now share one result.
- **Plain Ropes Carrying Power:** A normal rope could carry electricity like a steel cable. When a steel cable was broken, only the end that owned it forgot it was steel, so a new rope tied to the other end was treated as steel. Both ends are now cleared, a rope made with anything other than a Steel Cable always starts as a plain rope, and a rope is no longer considered steel just because of its far end. Blocks that still hold their own steel cable keep it.
- **Hermetic Detection Stuck:** If the hull scan ran into a part of the ship's plot that had no chunk yet, it threw the whole result away and started over, forever, so the ship could stay "not hermetic" with nothing in the logs. Such a spot now counts as open water, and after three incomplete scans in a row the result is used anyway.
- **`/submarine info` Pressure:** "Under pressure" only said yes once a hull block had already cracked. It now says yes whenever a sealed ship is below the surface, and the command also shows the depth limit of the weakest outer hull block.
- **Wind Vane Alignment on Swivel Sails:** The wind vane's alignment percentage now also works when the vane sits on the hull and the sail turns on a Swivel Bearing. The bearing makes the sail its own sub-level, and the vane only looked for sails on its own sub-level.
- **Rope Winch Goggle Info:** Looking at a Rope Winch with goggles showed nothing but the steel cable lines, because Deep Seas replaced the winch's whole goggle tooltip. Simulated's own lines (speed, stress) are back, and the steel cable network and energy are added below them.
- **Too Many Sails:** A ship carrying more sail groups than the shader can hold no longer writes past the end of the list. Extra groups simply don't billow.
- **Lava Bucket in the Boat Engine:** Fueling the engine with a lava bucket now gives the empty bucket back instead of eating it.
- **Menus From Afar:** The Boat Engine fuel menu now closes when you walk away from the engine, like any other container.
- **Oars Without an Oar:** Rowing now needs an Oar in hand on the server too, not only on the client.
- **Sails Pushing Players Into Walls:** A billowing sail pushing a player now moves them with normal collisions, so it can no longer shove them into a block.
- **Sail Collision Memory:** Sail smoothing data is dropped for ships that no longer exist, instead of piling up for the whole session.
- **Code Cleanup:** Removed dead code (`WaterInterfaceDepth`, `FloaterTuning`), unused textures and a stray model, useless try/catch blocks around plain math, and some per-tick allocations in the boat buoyancy, boat engine, sail shader and water culling code.

### Localization
- **Translations:** Added English (`en_us`) and French (`fr_fr`) translations for Create: High Seas (blocks, items, tooltips, helm messages, config screen), plus the new Client/Server config section names for Deep Seas.
- **New Strings:** English and French names for the sixteen Sails ("Sail" / "Voile"), the Electrolyzer alternator slot tooltip and output readout, the Onboard Computer Ponder scene and the new Electrolyzer Ponder step. The computer screen now says "NO PUMP FOUND" / "AUCUNE POMPE DÉTECTÉE" instead of "linked".

## [June 18, 2026] - Modded Fluids and Sodium Compatibility Fixes

- **Modded Fluids Compatibility:** Completely rewrote the fluid detection logic across the entire mod (Decompression Chambers, Ballasts, Electrolyzers, etc.) to use standard NeoForge Fluid Tags (`#minecraft:water`) instead of hardcoded vanilla blocks. The mod is now natively compatible with modded liquids like Terrafirmacraft's saltwater out of the box!
- **Mod Compatibility (Veil/Spotlights):** Fixed a shader compilation crash that occurred when using the "Spotlights or Something" mod (Veil rendering engine) alongside Sodium. Rewrote the Sodium shader injection using MixinExtras `@ModifyReturnValue` to gracefully allow Veil's mixins to apply their shader uniforms first, preventing `undefined variable "lodBias"` OpenGL errors.
- **Mod Compatibility (Sodium):** Lifted the official "incompatible" tag for Sodium. The mod is now fully compatible with Sodium!

## [June 17, 2026] - Performance & Dedicated Server Fixes

### Bug Fixes & Refactoring
- **Dedicated Server Crashes:** 
  - Fixed a critical issue preventing dedicated servers from starting due to `FlowingFluidMixin` and client-side code stripping.
  - Fixed a severe crash during server startup where a client-only rendering class (`SubLevelCrackRenderer`) was referenced within a common network packet, causing the Mixin pre-processor to crash and trigger a cascade failure of dependent mods (like Copycats+ and Create Aeronautics).
- **Decompression Chamber Performance:** Massively improved the decompression chamber's TPS performance during filling and draining by optimizing the compartment BFS (Breadth-First Search) to only run once per tick instead of for every block filled.
- **Fluid Duplication Fix:** Fixed a bug in the decompression chamber's fluid handlers that would incorrectly duplicate or void fluids by scaling transfer rates artificially. The block now correctly respects the 1:1 fluid mechanics.
- **Waterlogged Block Protection:** The decompression chamber will no longer accidentally destroy and replace waterlogged blocks (like slabs and stairs) when attempting to manage water levels inside the compartment.
- **Coordinate Mapping Accuracy:** Fixed a severe bug in `EntityWaterPhysicsMixin` where player coordinates were incorrectly calculated in global world space instead of local sublevel space, breaking airtight submarine suffocation/swimming checks.
- **Memory Leak Prevention:** Patched a static memory leak in the decompression chamber that prevented unloaded or destroyed chamber water blocks from being garbage collected.
## [June 15, 2026] - Barometer, Decompression Chambers & Implosion Mechanics

### New Blocks & Features
- **Create Aeronautics Compatibility:** Officially updated compatibility to fully support the new **Create: Aeronautics 1.3.0** update.
- **Barometer:** Added a new Barometer block and item. It displays the current pressure state relative to your submarine's weakest hull block (Acceptable, Warning, Critical) using a visual pufferfish and detailed tooltips. Mining it correctly requires an Iron Pickaxe.
- **Barometer Display Link:** The Barometer now updates Display Links at an incredibly fast 10 times per second (every 2 ticks), making connected displays instantly responsive to depth changes.
- **Commands:** Added the `/submarine findhole` command to help locate leaks and breached blocks in your submarine hull.
- **Decompression Chambers (WIP):** Enhanced the Ballast Vent to support a "CHAMBER" mode. You can now use ballast tanks to gradually fill or drain sealed airlocks layer by layer. *(Note: The decompression chamber system is currently in development and is currently unusable).*
- **Boat Support (WIP):** Continued groundwork for boat mechanics. *(Note: The boat system is currently in development and is currently unusable).*

### Physics & Implosion Mechanics
- **Smart Hull Breaches:** Breaking a block inside the submarine while under extreme pressure no longer instantly implodes the entire sub. The system now strictly verifies that the broken block is part of the *exterior hull* before triggering a catastrophic failure.
- **Copycat Support:** Added official support for Create and Copycats+ copycat blocks. The pressure system now dynamically reads the copied material to determine maximum depth and cracking behavior.
- **Creative Mode Safety:** Breaking the exterior hull while in Creative Mode no longer triggers an implosion, allowing you to safely build or modify your submarine at any depth.
- **Dynamic Airlock Implosions:** IN WIP; Don't USE Decompression chambers no longer implode at a hardcoded depth of 80 blocks if opened to the ocean without being filled with water first. They now dynamically check your submarine's hull strength and will only implode if you are in a "Warning" or "Critical" pressure state.
- **Visual Hull Cracks:** As your hull approaches its pressure limits, visible cracks will form and water will begin dripping into the submarine.
- **Wrench Repairs:** You can now repair these cracks before the hull gives way by right-clicking on them with the Create Wrench, which will reinforce the block and prevent implosion.
- **Adjusted Pressure Thresholds:** The "Warning" threshold on the Barometer and for the pressure system has been raised to 80% of your weakest hull block's maximum depth (previously 75%), giving you a larger safe margin before things become critical.
- **Config Auto-Reset:** Because the depth calculations and global caps have been entirely overhauled to allow blocks to go much deeper, the `submarine_hull.json` config file will be automatically regenerated (and the old one backed up) the first time you launch this version.

### Bug Fixes & Refactoring
- **Accurate Hull Detection:** Fixed a major bug where unsealed exterior areas (like the surrounding ocean) were evaluated as submarine compartments. This previously caused non-structural exterior blocks, corners, and decorations to incorrectly lower the submarine's total hull strength.
- **Copycat Wrench Priority:** Fixed a conflict where trying to repair a cracked Copycat block with a Wrench would unintentionally strip its applied material. The wrench will now strictly prioritize repairing cracks before allowing the block to be undisguised.
- **Login Desynchronization & Fog Fix:** Fixed an issue where logging into a world inside a submarine temporarily affected players with water physics and thick fog. The compartment scan penalty delay was removed, allowing immediate airtight verification upon chunk loading.
- **Dedicated Server Crash Fixes:** Fixed severe startup crashes on dedicated servers by ensuring optional dependencies (`lithostitched`, `fusion`) are strictly marked as optional in the `mods.toml`, and abstracting client-side rendering elements (`SubLevelCrackRenderer`) from common server Mixins.
- **Pulley Renaming:** Renamed internal references, blocks, and items from "Poulis" to "Pulley" for better clarity.
- **Rendering Fixes:** Implemented rendering fixes for water occlusion and resolved issues causing invisible blocks in production environments when using Veil/Flywheel shaders.

## [June 13, 2026] - Mod Splitting, Sable Physics & Connecting Glass

### Mod Architecture & Splitting
- **The Great Mod Split:** Separated the monolithic codebase into three distinct modular projects to streamline development and structure future content:
  - **Create: Deep Seas:** The main core mod (formerly *Create Submarine*), containing all submarines, buoyancy controllers, depth pressure mechanics, and core underwater tools.
  - **Create: Abyss:** A dedicated mod containing the Abyss dimension, custom deep-sea biomes, bioluminescent plants/fauna, physical lianas, and the PDA overlay namespace.
  - **Create: High Seas:** Initial groundwork added for an upcoming mod focused on boat support, custom sails, and wind dynamics.

### New Blocks & Features
- **Arresting Hook:** Added the Arresting Hook block, block entity, custom item rendering, and creative tab placement for slowing down or docking vessels.
- **Pressurizer Connected Glass:** Added optional support for the Fusion mod, introducing connected glass textures for all pressurized glass variants.

### Physics & Integration
- **Sable Force Queuing:** Fully integrated ballast tanks and floaters with Sable's physical force-queuing system. Buoyancy forces are now calculated block-by-block and submitted as aggregated clusters for smoother physics updates.
- **Sable UI Force Clustering:** Merged multiple ballast and floater indicators into single aggregated points in the submarine diagram UI (displaying total force and count) to avoid screen clutter.
- **Waterwheel Propulsion:** Added support for waterwheels in sublevels. Large waterwheels now dynamically apply thrust and impulses to the sublevel's physical body.

### Performance & Optimizations
- **Airtight Check Caching:** Added per-tick caching to `EntityWaterPhysicsMixin` for airtight compartment checks, preventing redundant compartment lookups when multiple entities check their suffocation status in the same tick.
- **Level-Aware Compartment Lookup:** Optimized `CompartmentTracker` to ignore sublevel entries belonging to different dimensions during containment scans.
- **Efficient Vegetation Clearing:** Reworked the submarine placement clearing code to sweep foliage/kelp by chunk section rather than block-by-block, respecting world height bounds.

### Bug Fixes
- **Pressurizer Glass Translucency:** Changed pressurized glass blocks to inherit from `TransparentBlock`, fixing rendering glitches and allowing other blocks to be properly visible through them.
- **Pocket Fog & Water Culling:** Resolved rendering glitches where fog and water culling wouldn't update properly inside sealed air pockets within sublevels.
- **Sodium Rendering Integration:** Fixed a Sodium water occlusion issue by switching to `RenderSystem` texture binding inside `SodiumWaterOcclusionBridge`.
- **Pressure Crack Repair Syncing:** Reworked wrench repairs to decrement crack stages properly and broadcast block updates with correct block IDs to prevent desyncs.

### Under the Hood
- **Cleaned Bundled Assets:** Removed outdated, temporary bundled files (`temp_aero`) and unused assets to optimize the mod footprint.
- **Project Structure Documentation:** Added a detailed explanation of the project structure to the repository's `README.md`.

### Localization
- **Translations:** Synced and updated keys for English (`en_us`), French (`fr_fr`), Russian (`ru_ru`), and Simplified Chinese (`zh_cn`).

## [June 7, 2026] - Submarine Occlusion & Suffocation Fixes

### Bug Fixes
- **Airtight Submarine Suffocation:** Fixed a critical bug where players would suffocate and lose bubbles while safely inside an airtight submarine. NeoForge's custom fluid type checks (`getEyeInFluidType`) now correctly recognize the submarine's interior as empty, eliminating server/client desyncs and ghost bubbles.
- **Corner & Walking Occlusion Glitches:** Replaced the grid-based position checking with exact decimal (`Vec3`) local-space physics. The water occlusion tolerance has been expanded to include the physical boundaries of the hull itself (`VISUAL_UNION`). Players will no longer start drowning when walking on the floor, getting pushed into walls, or standing in tight corners of a heavily pitched submarine.
- **Swimming Prevention:** The game will now forcefully prevent players from entering swimming mode while inside the submarine's occluded area.

## [June 6, 2026] - Cookiecutter Sharks, Naval Mines, Steel Cable Power & Propellers

### New Blocks & Features
- **Cookiecutter Shark:** Added a new deep-sea predator with its own model, swim/idle animations and a latch-and-struggle attack behavior.
- **Underwater Mines:** Added deployable naval mines that float to hold their depth, arm when a ship or creature gets close, and detonate with a large underwater shockwave — camera shake, launched debris, and area damage.
- **Submarine Propeller:** Added a submarine propeller block with configurable power, client-side bubble/particle wake, and dedicated rendering.
- **Steel Cables & Electrification:** Added steel cable visuals (connector and winch variants, ponder scenes) and a cable electrification system that carries energy along cables, throws sparks, and shocks entities touching a live line.
- **Amphistium:** Added the Amphistium, a glowing schooling fish that spawns in the Abyss biome.

### Configuration & User Interface
- **Update Notifications:** Added an in-game update checker that queries Modrinth and shows a screen when a newer Deep Seas version is available, with the changelog and an "ignore this version" option.
- **Lithostitched Reminder:** Added a startup screen that prompts you to install the recommended Lithostitched dependency when it is missing.
- **Item Tooltips:** Mines, propellers and floaters now show descriptive tooltips.

### Bug Fixes
- **Mines no longer detonate on their own wreckage:** Fixed mines randomly exploding while you broke blocks off them to make them resurface. Each detached chunk becomes a brand-new sublevel spawned right next to the mine, which the proximity trigger mistook for an approaching ship. Debris now inherits the mine's owner at the exact moment of the split and is ignored by the trigger, while real ships still set the mine off.
- **Mines no longer punch holes in the ocean:** Fixed mine explosions spawning their flying-block debris in a way that silently replaced the block at the target world position, carving air pockets into the surrounding water and terrain. Debris is now thrown without ever touching the parent world.
- **Mine depth-keeping no longer lags the server:** Replaced the once-per-second full-volume scan each mine ran (to share buoyancy between mines) with live O(1) tracking, preventing TPS drops on large hulls.
- **Cable energy network spam:** Throttled the block-update packets sent while energy flows through cables (roughly twice a second instead of every tick) to stop network lag.
- **Update checker visibility:** Marked the update-check result fields `volatile` so the render thread reliably sees the values fetched on the background network thread.
- **Corrupt client state file:** Loading an empty or corrupt `create_submarine_client_state.json` no longer throws — it now falls back to defaults.
- **Sturdier cable collisions:** Hardened cable-versus-player collision (sublevel and parent-level handling, null rope-manager guards) and made the winch energy store thread-safe.
- **Pulley concurrency & speed:** Clamped pulley slide speed to a configurable maximum and deferred block destruction/particles to the server tick to avoid concurrency crashes.
- **Fog submersion detection:** Reworked the underwater fog check to skip occluded positions and confirm actual water, fixing incorrect fog states.
- **Reduced particle spam:** Lowered ballast-vent and water-thruster particle counts and frequency.
- **Double-unregister guard:** Submarine state is now only cleared once the driver claim is actually released, preventing double-unregister glitches when several control blocks are present.

### Under the Hood
- **Submarine Driver Registry:** Added a registry that grants a single exclusive "driver" block per submarine (hull controller / oxygen diffuser) using priorities and stale-claim eviction.
- **Mine float guard:** A mine bolted onto a contraption larger than 5 blocks no longer acts as a ballast/floater.
- **Pressure membership helper:** Centralized the "is this position part of the ship" check (`isWithinShip`) used by the pressure system.
- **Hull scan budgeting:** Added dynamic scan delay/budget logic to the hull controller to spread out leak scanning.
- **Access transformer:** Added an access transformer so falling-block debris can be spawned without destructive world side effects.

### Localization
- **Translations:** Updated Simplified Chinese (`zh_cn`) and Russian (`ru_ru`) translations.

## [May 30, 2026] - Persistent Lianas, Fruit Reattachment & Configurable Oceans

### Bug Fixes
- **Lianas Surviving Reload:** Fixed creepvines breaking apart and floating to the surface after leaving and rejoining a world. Their topology (which segment anchors to the seabed, parent/child links, attached fruits) was never actually persisted — the spawn-time block-entity lookup silently failed, so nothing survived a reload. The full chain layout is now saved to a dedicated registry and every physics joint is rebuilt deterministically on load.
- **No More Self-Fighting Lianas:** Fixed lianas jittering, folding and collapsing when bumped into after a reload. Each stacked liana block was running its own physics and stacking buoyancy/player forces several times over; only the segment's centre block now drives the simulation.
- **Fruits Staying Attached:** Fixed creepvine fruits randomly dropping off on reload depending on chunk load order. Each fruit's rest position is now remembered and the fruit is snapped back into place before its joint is rebuilt, so it always reattaches where it belongs.

### Configuration & User Interface
- **Configurable Ocean Depth:** The Deeper Oceans feature is no longer locked to a fixed 10 blocks — a new `deeperOceansDepth` option (default 10, up to 256) lets you choose how far below vanilla the sea floor sits, with an in-config warning that large values can badly hurt world-generation and rendering performance.
- **Deep Seas Welcome Screen:** Added a one-time welcome screen shown in front of the main menu, recommending you set up your Deep Seas preferences before diving in. It offers a button straight to the mod configuration and a "maybe later" button; either choice is remembered in the config TOML so it never shows again.

### Under the Hood
- **Reusable Plant Persistence:** Generalized the liana save system into a plant-agnostic `PlantPhysicsRegistry` that will back future physical plants, made it the single source of truth for segment topology, and dropped the redundant (and unreliable) block-entity NBT copy.

## [May 29, 2026] - Abyss Dimension, Physical Lianas & Big Optimizations

### New Blocks & Features
- **Abyss Dimension:** Reintroduced the custom deep-ocean Abyss dimension with dedicated biome configs and custom worldgen.
- **Physical Lianas & Seeds:** Added creepvines (submarine lianas) and creepvine seeds that grow, flow with water currents, and simulate realistic physics inside sublevels.
- **PDA & Sound Effects:** Added a custom PDA menu overlay and registered new ambient/warning audio cues for Leviathans (roars and class detection warnings).
- **New Diagnostics Command:** Added the `/submarine info` command to inspect current hull integrity, depth, crack counts, and whether your sub is hermetically sealed or breached.
- **Spawning Command:** Added `/submarineliana spawnradius` to spawn creepvines inside a radius with custom density/probabilities.
- **Client RAM Boost:** Allocated 20 GB of RAM for the game client config to handle large sublevel structures smoothly.

### Performance & Optimizations
- **Global LOD System:** Added a new LOD Optimizer (`LianaLODOptimizer`) that automatically pauses/freezes physics and ticking on distant creepvines to save CPU/FPS.
- **Spawning Queue:** Spawning multiple lianas in a radius is now staggered (closest to the player first) and freezes other lianas during creation to prevent lag spikes.

### Bug Fixes
- **Liana Sublevel Lighting:** Fixed an issue where lianas in Sable sublevels rendered pitch black. They now dynamically fetch and reflect the real-world block light values (torches, glowstones, shaders) around them.
- **Precise Pressure Calculations:** Completely reworked pressure depth logic to measure depth block-by-block relative to the actual water surface rather than checking the center of the sub globally.
- **Sinking & Crash Handling:** Added a limit to how many blocks can implode at once to prevent audio/particle lag spikes, and ensured oxygen/life-support blocks are immediately destroyed when a sub sinks.
- **Stability Fixes:** Resolved rare `NullPointerException` and chunk-loading issues in the leak/compartment scanner, and fixed a concurrency deadlock in the Sable snapshot queue.

## [May 27, 2026] - Experimental Branch Updates & Enhancements

### Configuration & User Interface
- **In-Game Mod Configuration UI:** Implemented a new custom split-pane configuration screen (`HullStrengthConfigScreen`) to allow editing block-by-block depth limits and implosion chances directly in-game.
- **Config Persistence:** Changes made in-game are automatically saved to `config/submarine_hull.json` and applied at runtime.

### Pressure Physics & Visual Glitches
- **Refactored Stress Cues:** Restructured pressure calculations to only play metal creaking stress sounds and spawn water dripping particles when a block actually sustains crack or implosion damage. Blocks set to 0% implosion chance are now completely silent and dry.
- **Configurable Floater Limits:** Updated the Floater block to respect customized depth limits set in the configuration instead of using a hardcoded threshold.

### New Items & Create Integration
- **Survival Friendly:** All custom blocks and items (including Phycological Membranes, Pressurized Glasses, and Floater variants) now have proper survival recipes, making the mod fully survival-friendly.
- **Phycological Membrane:** Registered and added the Phycological Membrane item, which can only be crafted by pressing a Kelp block under a Create Mechanical Press.
- **Iron & Copper Pressurized Glasses:** Reworked the generic glass pressurizer block into distinct Iron and Copper variants with new recipes, models, and textures.
- **Colored Floater Variants:** Added crafting recipes for Floater blocks in all vanilla wool colors.

### Abyss Biome & Custom World Generation
- **Abyss Biome:** Introduced a deep ocean Abyss biome ALPHA
- **Biome Modifiers:** Implemented world generation modifiers for amplified, large biomes, and default world types.

### Renders, Compatibility & Performance
- **Early Startup Config Access Fix:** Resolved an initialization crash (`IllegalStateException: Cannot get config value before config is loaded`) in `PermanentWaterCullingTest` that occurred when early-ticking client mods (such as Xaero's Train Map) triggered ticks before NeoForge configurations loaded.
- **Sodium & Veil Shaders:** Integrated critical rendering compatibility mixins for Sodium and Veil pipelines.
- **Sable Network Synchronizations:** Resolved package de-synchronization issues under active tracking states.
- **General Asset Cleanup:** Removed outdated model textures and unused assets to optimize mod footprint.
- **Sable Dependency Upgrade:** Upgraded Sable to version 1.2.2 for improved sub-level physics and performance stability.


## For developers (Modrinth Maven)

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter { includeGroup "maven.modrinth" }
    }
}

dependencies {
    implementation "maven.modrinth:create-deep-seas:2.0.0"
}
```
