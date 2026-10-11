# DashAuction for Minecraft 26.3

A **Fabric Java client mod** that helps host a timed auction by observing *incoming server-generated payment notifications* on DonutSMP. **Nothing is refunded or delivered automatically.**

### Features
- **/dashauction** opens a GUI with a paged, searchable icon grid of every registered Minecraft item.
- Enter the minimum accepted **single payment**, item worth (information only), and duration in seconds.
- The highest qualifying individual payment becomes the leader. Example: Alex pays 1B, then Steve pays 1.1B before the timer runs out; Steve takes first place.
- It **continues listening until the end of the timer** even when a leader exists.
- Payments observed **at or after the deadline do not count**. Ties don't replace the earlier leader.
- Winner shown in chat and in the GUI. Closing the GUI does not stop the auction.

### Install
Build using Gradle 9.7.1 with Java 25, or download the JAR artifact from a green Actions build. Install Fabric Loader, the matching 26.3 Fabric API, and this client mod in the client mods folder. This is a Java client mod, not a Paper server plugin or Bedrock add-on.

### How to start
1. Join DonutSMP and type **/dashauction**.
2. Select an item from the grid or find it using search.
3. Type minimum payment (e.g. 1b), worth (e.g. 2b), duration in seconds (e.g. 60).
4. Enter the **exact server payment notification** text but replace the username with **{player}** and monetary amount with **{amount}**. Example only: `{player} paid you ${amount}`. DonutSMP's real message format is not yet confirmed.
5. You can check whether a sample string matches using **/dashauction preview <message text>** (this does *not* submit a bid).
6. Click START. Remain connected to the same server until the timer ends.

### Limitations
- Reads **server-generated GAME notifications**, not ordinary player messages (players can spoof player chat).
- The timestamp is when the client sees the payment message, *not* DonutSMP's server-side transaction timestamp. Late notifications, lag, unexpected formats, disconnects, and missed messages can affect the leader.
- There is no DonutSMP balance or transaction integration; no automatic payment verification, item handoff, item reservation, or refunds.
- Each transfer is evaluated individually, not accumulated across multiple payments.
- A disconnect cancels the currently running auction.
- The received-payment text format is saved to `config/dashauction-payment-format.txt`.

### Development
The `coreSelfTest` task checks numeric suffixes, high-bid replacement, ties, minimums, strict deadline handling and post-deadline locking. GitHub Actions compiles the mod and publishes a JAR artifact only after a green build.
