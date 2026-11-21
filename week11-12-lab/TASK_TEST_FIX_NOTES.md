# Week 11–12: OrderSequenceTracker unit-test fix

The existing `OrderSequenceTracker.record()` checked only the latest received sequence against the current list length. For a list like `[2, 1, 3]`, the third call incorrectly reported `false` despite the earlier ordering violation.

This patch replaces the last-element check with `!isOrdered(seqs)` while holding the sequence-list monitor, making `record` report whether the *whole currently observed sequence* is out of order.

Only `OrderSequenceTracker.java` changes. The previous six lab experiments remain valid. Run `mvn test` in both services and confirm both BUILD SUCCESS before committing.
