# Contributing to Lightning

Thanks for wanting to help out. Whether it's a bug report, an idea, a fix or a kind word, it all makes Lightning better.

*By contributing to Lightning, you agree to license your changes under the [GNU General Public License v3.0 or later](LICENSE).*

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Asking for help

The issue tracker is for bugs and feature requests. For setup help and general questions, the [documentation](https://hub.denizenmc.com/docs/lightning) and the [Support Discord](https://support.denizenmc.com) are the quickest way to an answer.

## Reporting bugs

[Open a bug report](https://github.com/EliteDenizen/Lightning/issues/new?template=report-a-bug.yml) and fill out the form. Before you do:

- Update to the latest Lightning and try again. It may already be fixed.
- Search the [existing issues](https://github.com/EliteDenizen/Lightning/issues) to see if someone has reported it.
- Turn on `/lightning debug` while you reproduce it. The console output often points straight at the cause.

If you think you've found a way around Guard, or another security problem, please don't open a public issue. See the [security policy](SECURITY.md) instead.

### How bugs are labelled

New reports start as `bug: unconfirmed`. That means nobody has reproduced it yet, not that we don't believe you. Once we can make it happen ourselves it becomes `bug: confirmed`.

Some bugs come from Paper, PacketEvents, Minecraft or another plugin rather than Lightning. Those are labelled `bug: upstream`. We may still be able to work around them, but the real fix belongs elsewhere.

You can help with unconfirmed bugs too. Follow the steps in the report and leave a comment saying whether it happens for you, along with your Lightning and Paper versions.

## Suggesting features

[Open a feature request](https://github.com/EliteDenizen/Lightning/issues/new?template=request-a-feature.yml). One idea per issue, please, and tell us how it would help your server. Real examples are the most convincing.

Lightning is about making text look and behave the way you want. Ideas that belong in a chat, economy or permissions plugin are usually better suited there, so we may close those with a pointer in the right direction.

## Submitting code

Pull requests are welcome. To make sure your time is well spent:

- **Bug fixes** should have an issue first. If there isn't one, open it before your pull request.
- **New features** should be discussed and agreed on in an issue before you start. Issues labelled [`status: open to PR`](https://github.com/EliteDenizen/Lightning/issues?q=is%3Aissue+is%3Aopen+label%3A%22status%3A+open+to+PR%22) are ready for anyone to pick up, and [`status: good first issue`](https://github.com/EliteDenizen/Lightning/issues?q=is%3Aissue+is%3Aopen+label%3A%22status%3A+good+first+issue%22) is a friendly place to start.
- **Keep it focused.** One fix or feature per pull request. Unrelated changes bundled together are hard to review and won't be merged as-is.

### Getting set up

You'll need:

- Java 21
- Git
- An IDE. [IntelliJ IDEA](https://www.jetbrains.com/idea/download) works well.

Fork the repository, clone your fork, and create a branch for your change:

```sh
git clone https://github.com/<your-name>/Lightning.git
cd Lightning
git switch -c fix-tab-list-gradients
```

Build with:

```sh
./gradlew build
```

The JAR ends up in `build/libs/`. To try it out, `./gradlew runServer` starts a local Paper test server with Lightning installed.

### Writing your change

- **Match the code around you.** Use the same naming, structure and comment style as the files you're working in.
- **Comment the why, not the what.** A short note explaining a decision is worth more than a line restating the code.
- **Think about Folia.** Lightning runs on both Paper and Folia, so avoid assumptions about a single main thread.
- **Keep it light.** Lightning sees every message on the server. Ordinary messages should skip work they don't need, and anything reusable should be cached.
- **Keep players safe.** Text that players type must never be treated as formatting. If your change touches how input is processed, make sure Guard still covers it.
- **Test it on a real server.** Check the feature you changed, and anything nearby it could affect.

If players or server owners will notice your change, include a short line in your pull request describing it the way it should read in the changelog.

### Opening the pull request

Push your branch to your fork and open a pull request against `master`. Fill out the template: what changed, why, and how to test it. A clear "how to test" makes reviews much faster.

We'll review it, and may ask questions or suggest changes. Just push new commits to the same branch to update it.

## Supporting Lightning

Lightning is free and open source, and always will be. If it's made your server better and you'd like to give something back, you can [buy me a coffee on Ko-fi](https://ko-fi.com/elitedenizen). It's never expected, always appreciated.

Not in a position to donate? Leaving a review on [Modrinth](https://modrinth.com/plugin/BzegqUOE), helping someone on Discord, or confirming a bug report helps just as much.
