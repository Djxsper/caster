# iPad screenshots

Captured on an **iPad Pro 11-inch (M4)** simulator, 1668 x 2420, portrait,
light appearance, by `.github/workflows/ipad-simulator.yml`.

Full resolution, unlike `docs/screenshots/`, which is downscaled to 420px
because those images are embedded in the README. These are here to judge
layout on a large screen, so the detail is the point.

Portrait only: simctl has no orientation command, and rotating through
Simulator.app needs accessibility permissions a hosted runner will not grant.
The touch games are shown at rest, since nothing simulates fingers on glass.

To refresh them:

    gh workflow run ipad-simulator.yml
    gh run download <run-id> -n caster-ipad-screenshots

Optional inputs: `-f device="iPad mini"`, `-f appearance=dark`.
