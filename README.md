# Set

The one place that says which commit of each project builds together, with the SHA-256 of every file those builds
made, and the platform they build against: the JDK builds for Linux and Windows, Gradle and Cleanroom.

`set.json` (schema 1) holds:

- `projects.<name>`: a project's line: its branch, the commit on it, and its files (`artifacts.<name>`: SHA-256,
  file name, the glob that finds it, the check that builds it). A line carries `url` only for a public repository.
- `third.<name>`: a third-party file, by coordinate or URL, with its SHA-256 and licence.
- `platform`: the JDK build per major for each operating system, the Gradle distribution, Cleanroom and
  CleanroomGradle versions and the plugin repositories.
- `holds.<project>[:<artifact>][@<consumer>]`: one artifact, or one consumer's view of a project, kept on another
  commit, with a reason and the date it began.
- `doctor.exempt`: paths a check for copied pins leaves alone.

Lines move only by landing (`wb land` in Workbench): the landed commits' checks pass, their files are kept by
SHA-256, and one commit here records the move, after which each project's set-named branch is fast-forwarded to
its line. A commit here is never written by hand.

## Building without Workbench

Clone this repository and the projects you need side by side, then run `python tools/checkout.py`: it checks each
sibling out at the commit `set.json` names and prints the order to build in. A build made this way is a
development build: its bytes are not checked against the set.

`python tools/check_set.py` checks `set.json` against the schema.
