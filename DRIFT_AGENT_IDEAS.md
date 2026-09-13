# Drift Investigation Agent — Design Notes

This is a scratchpad, not a spec. Write in whatever's on your mind under each
section — half-sentences, questions, "not sure, maybe X or Y" are all fine.
We'll turn this into an actual design together once it has enough in it to
argue with.

Each section has a few prompting questions in *italics* so you're not staring
at a blank page — delete them as you write, or ignore them and go your own
direction.

---

## 1. What triggers the agent

*When a VAST_ONLY drift gets created — right then? Once a day after the full
reconciliation run finishes? Only when someone clicks a button? Does every
drift get investigated, or only some?*

(your notes here)

---

## 2. What "investigating a drift" means

*Forget AI for a second — if a human on the Account Sync team got handed one
of these drifts, what would they actually go click through / query / check
first? What would make them say "ah, that's why" vs. "no idea, escalate"?*

(your notes here)

---

## 3. What the agent should be allowed to look at

*In the real system: DB2 audit trail, EventBridge event history, SQS
dead-letter queue, transfer job logs? In this repo, none of that is real —
so which of these do we fake, and what should the fake version look like?*

(your notes here)

---

## 4. Root cause hypotheses

*What are the actual ways an account could legitimately end up in DB2 but
never make it to NewT? e.g. the transfer job crashed, a message landed in a
DLQ and nobody retried it, a duplicate got silently dropped, a timing race
where EOD ran before the transfer finished, a bad field caused a mapping
error... Brainstorm as many as you can think of — these become the things
the agent is trying to distinguish between.*

(your notes here)

---

## 5. What a finished diagnosis should look like

*Once the agent is done, what does the output need to contain to be useful?
Just a sentence? A root cause category + confidence + evidence it found +
a suggested next step? Who reads this — a person on a dashboard, or does it
feed something else automatically?*

(your notes here)

---

## 6. Open questions / things you're unsure about

*Anything you're not sure how to think about yet — throw it here and we'll
work through it together.*

(your notes here)
