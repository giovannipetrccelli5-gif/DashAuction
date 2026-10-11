# DashAuction for Minecraft Java 26.3

Fabric client mod for hosting timed, **in-game currency** auctions on DonutSMP. It watches incoming server-generated payment notifications and displays a live highest-paid leaderboard.

**No automatic item delivery. No refunds. No outgoing payment commands.**

## Features
- Type `/dashauction` to open setup with a searchable, paginated icon grid of all registered items.
- Enter an item worth for display, auction duration in seconds, and the exact payment notification text.
- **Any positive payment qualifies**, with no required minimum.
- All payments from the **same player are added together** (case-insensitively). Example: Alex pays 500M then 600M = 1.1B total; Steve pays 1B and remains below Alex.
- Bids are monitored for the **entire timer**, even after somebody takes the lead.
- Highest **total paid** at the deadline wins; ties preserve the earlier leader.
- An in-game **HUD panel appears only while hosting an auction**, showing the selected item, its displayed worth, countdown, the highest payer and next two payers.
- Once the timer ends, it displays the winner's name and total on screen for 20 seconds, and also sends the winner's name to client chat. The setup GUI shows the last winner.
- After that, the HUD disappears. When not hosting an auction there is no auction HUD.
- Payments received at or after the end time never count.

## Install and use
Requires **Minecraft Java 26.3, Fabric Loader, Fabric API and Java 25**.
1. Download a compiled mod JAR from a **successful GitHub Actions build** on the [feature PR](https://github.com/giovannipetrccelli5-gif/DashAuction/pull/1). The PR remains draft until validated.
2. Add the JAR to your **client** `mods` folder along with the matching Fabric API.
3. Connect to DonutSMP. Type `/dashauction` and pick an item.
4. Enter estimated item worth, duration in seconds, and the payment notification template.
5. Replace the sender with `{player}` and the amount with `{amount}`. Example format only: `{player} paid you ${amount}`. Actual DonutSMP notification wording is **not verified**.
6. Run `/dashauction preview <example message>` to test the configured format without placing a bid.
7. Click START. The live HUD appears and continues working with the setup screen closed.

## Limitations and fairness
- This is an **informational client-side tracker**, not an authoritative server payment integration. Incoming notification message format and authenticity must be verified before relying on results.
- It only processes GAME/system messages received from the server, **not ordinary player chat**, to avoid accepting spoofed chat lines.
- Timings use monotonic **client receipt time**, not the server's payment processing time. Lag/disconnects/missed notices can change the apparent result.
- Repeated server notifications of the same payment could be counted twice; there is no server transaction ID or deduplication API. Keep records and verify the winner yourself.
- Cannot deliver the item, return losing amounts, withdraw money or verify account balances. Always explain no-refund terms to participants in advance.
- A server disconnect cancels the active auction; no new payments are accepted until a new auction begins.
- This mod does not run on Bedrock clients, only the host's Java client.
- Format is stored in `config/dashauction-payment-format.txt`.

## Development
Run `gradle build` with Java 25. GitHub Actions builds the Fabric client mod and runs core state-machine tests, including accumulation, ties and precise deadline handling.
