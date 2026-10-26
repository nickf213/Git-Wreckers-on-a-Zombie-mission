# Log book: Week 10 (streams, lambdas and collectors)

> DRAFT. Rewrite this in your own words before submitting, and add an AI use statement if your course requires one.

**Student:** Nicholas Fang
**Class worked on:** `Zombie` (all of this week's changes are in `Zombie.java` and `test/ZombieStreamTest.java`)

## What I did

**1. Replaced the target-search loop with a stream.**
`findTarget()` used to loop over nearby entities, skipping soldiers and hidden humans and tracking the closest one by hand. I moved the filtering into `visibleCivilians(World)`, which returns a lazy `Stream<Human>`. `findTarget()` ends it with `min(comparingDouble(this::distanceTo))` and `orElseThrow`, so it now throws our own `TargetNotFoundException` instead of returning `null`. The same stream is reused for counting, so the "who can I see" rule lives in one place.

**2. Moved the State pattern into `Zombie` as a lambda enum.**
The zombie had two hidden modes (wandering and chasing) inside one `update()`. I made them a private `Mode` enum where each constant holds its behaviour as a `BiConsumer<Zombie, World>` lambda. `startChase()` and `stopChase()` handle the switches.
- *Alternatives I considered:* an if/else on a flag (the original, which gets messy as modes are added), an enum with a switch (still one big method), and Strategy as `Military` uses it (a strategy swaps an algorithm but doesn't decide when to switch, which is the core need here).
- *What I did first:* I started with separate `ZombieState`, `WanderingState` and `ChasingState` classes, then folded them into `Zombie`. With only two small states, separate classes were mostly boilerplate, and lambdas replace what would otherwise be a class hierarchy.
- *Trade-off:* `Zombie.java` is longer. If I add more states (fleeing, feeding), separate classes would be worth it again.

**3. Three collectors that describe the ecosystem.**
- `civiliansByDistanceBand` uses `Collectors.groupingBy` to count visible civilians in CLOSE, MEDIUM and FAR bands.
- `visibleCivilianEnergy` uses `Collectors.summarizingDouble` for min, max and average energy.
- `preyScore` uses a custom `Collector.of` (supplier, accumulator, combiner, finisher) that adds up how close each civilian is.

**4. A parallel stream, with reasoning about thread safety.**
`Zombie.totalPreyScore(World)` sums every zombie's prey score with `parallelStream()`. It is safe because each task only reads the world and its own zombie, the lambdas hold no shared state, and `DoubleStream.sum()` combines the results. `World` itself is not thread-safe, so this must only be called between ticks. I checked it against the sequential sum with 40 zombies and 30 humans, and again in a unit test with 20 and 15, and they matched. At this size the parallel version is probably not faster, because the overhead is larger than the work, so I'd describe it as a demonstration of the technique, not an optimisation.

**5. Using the metrics in behaviour.**
- `frenzyMultiplier` turns the prey score into a speed boost of up to 1.5x when a crowd is in view, plus a 0.25 lunge when someone is in the CLOSE band (1.75x at most).
- Infected humans now rise as new zombies (capped at 12, counted with a stream), so the population can grow.
- I raised the base chase speed to 2.4, because humans move at 2.0 and a slower zombie could never catch one.

## Testing
`ZombieStreamTest` has six checks: what a zombie can see, picking the closest target, throwing `TargetNotFoundException`, the prey score value, the parallel total matching the sequential one, and a human turning into a zombie. I confirmed a test can fail by changing one expected value in a scratch copy. I also ran `Main` under a virtual display for several seconds with no errors, and simulated its starting world for thousands of ticks.

## Trade-offs and what I learned
- **Game balance:** the frenzy, lunge and faster chase make zombies much deadlier in groups. That is more interesting, but I tuned it by feel and measurement, not against a target.
- **Spread is rare in the default game.** With `Main` unchanged there is one starting zombie, and the soldiers shoot it before it reaches anyone (0 of 8 test runs showed any spread). In an earlier test, two extra starting zombies at chase speed 2.4 gave spread in 8 of 8 runs, though that was before I added the lunge. I did not change `Main`, because it is a teammate's file. I wrote the suggested two lines in my notes for the team.
- **Streams made the rules easier to read, but not always shorter,** and a lazy stream shared between methods needs care: each caller must finish it with exactly one terminal operation.

## What could be improved
- `civiliansByDistanceBand` and `visibleCivilianEnergy` are only used by tests and the lunge. They could feed the on-screen display (`SimPanel`), which is another teammate's class.
- CI only runs `HumanFoodTest`, so `ZombieStreamTest` is not run on GitHub yet.
- `Main` needs the extra zombies for the spread to show up, and the balance numbers should be agreed with the team.

## Git and teamwork this week
Small commits with one-line messages, my Zombie work in PR #1, and a comment-only review of the team's code in PR #2 so teammates can reply on the exact lines.
