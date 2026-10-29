# Security Policy

Lightning sees every message on your server, so we take security seriously. Thanks for helping keep servers safe.

## Supported versions

Only the latest release of Lightning receives fixes. Please make sure you can reproduce the problem on the latest version before reporting it.

## What counts as a security issue

- Players getting tags, click events or placeholders interpreted when they should stay plain text, also known as a Guard bypass.
- Players running commands, gaining permissions or reaching admin tools they shouldn't have access to.
- Anything that lets a player crash the server, disconnect others, or read data they shouldn't see.

If you're not sure, report it privately anyway. We'd much rather hear about it twice than not at all.

## Reporting a vulnerability

**Please don't open a public issue.** Instead, report it privately in one of these ways:

- Use [GitHub's private vulnerability reporting](https://github.com/EliteDenizen/Lightning/security/advisories/new).
- Message a maintainer directly on the [Support Discord](https://support.denizenmc.com).

Include the Lightning, server and PacketEvents versions, the steps to reproduce it, and what an attacker could achieve. We'll confirm we've received it, keep you updated while we work on a fix, and credit you in the changelog if you'd like.
