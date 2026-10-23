# `:feature:history:impl`

Shows component operations grouped by local calendar day, with expandable PM/IFW snapshots. Users can confirm undo of one operation or all eligible operations since the start of a day. Confirmation counts distinct components.

History is retained for 90 days in a separate Room database. Reset IFW and undo rows remain visible without an undo action. Operations containing failed components remain available for retry; successful and already-in-target operations are marked undone. Every undo invocation has a row, including an undo with no changes.

The screen uses `core:data` for history and `core:domain` for undo. It depends only on its own feature API; Settings receives its navigation callback from `app-compose`.
