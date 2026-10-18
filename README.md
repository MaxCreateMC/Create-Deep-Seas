<img width="96" height="96" alt="Create Deep Seas logo" src="https://github.com/user-attachments/assets/0bd59af1-edf3-429d-b0b6-4cb3abb54263" />

# Create: Deep Seas

[![Powered by Sable](https://cdn.modrinth.com/data/cached_images/d13ac17ef50ed090e7d21fcae0caf8958eeece3d.png)](https://modrinth.com/mod/sable) [![YouTube](https://img.shields.io/badge/YouTube-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://www.youtube.com/@Maxenonyme) [![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/Ucsj7fatMj)

**Create: Deep Seas** (also known as Create Submarine) builds on Create and the Sable physics engine to let you build, pilot and survive in fully working, physics-based submarines.

<img width="2048" height="606" alt="Banner" src="https://github.com/user-attachments/assets/10e30577-d1d7-4649-8186-6cd15b424002" />

The mod comes in **three parts**, all included in the same jar:

- **Deep Seas**: pressure, oxygen, ballasts, sonar, onboard computer and everything else about submarines.
- **High Seas** <img src="https://cdn.modrinth.com/data/cached_images/3d017ae305255659491557bd546f6bcc63f13ff8.png" alt="High Seas logo" width="24">: boats, sails, wind, engines, anchors and flooding.
- **The Abyss**: a future biome and dimension with creatures and structures, giving a real reason to build better submarines. Still in development, only active in the dev environment.

## Dependencies

- [Create](https://modrinth.com/mod/create)
- [Create Aeronautics](https://modrinth.com/mod/create-aeronautics)
- [Sable](https://modrinth.com/mod/sable)

## Compatibility

Supported: Sodium, [Copycats+](https://modrinth.com/mod/copycats), [Wakes](https://modrinth.com/mod/wakes-reforged).

Highly recommended: [Iris & Oculus Flywheel Compat](https://www.curseforge.com/minecraft/mc-mods/iris-flywheel-compat), [Iris Veil Compat](https://modrinth.com/mod/iris-veil-compat).

Known issues:
- **Iris**: the coloured Veil lights of the Sonar and the Alarm are turned off, and sails don't billow with a shader pack.
- **WaterWorks**: breaks the water culling inside ships for now.

## Contributing

**You can propose anything you want**, as long as it fits the mod and I approve it. I really encourage you to add content; the ARR license doesn't stop you from doing so.

The code is split into three packages, one per part:

- `com.maxenonyme.createsubmarine`: Deep Seas, with the submarine blocks, the pressure and compartment systems and the water culling.
- `com.maxenonyme.highseas`: High Seas, with boats, sails, wind and buoyancy.
- `com.maxenonyme.AbyssDimension`: The Abyss, with the dimension, the creatures and the physical lianas.

## Credits

- **Developers:** MaxCreateMC, Cogfly, Zinc Studios
- **Contributors:** T418, Soub
- **Artists:** Marume, RandomePigeon, Funado
- **Alpha testers:** Gothci, Myxma, sant, qkback, Kuko, Tormi, Plasmori, Deron4iik
