package lab.problemlab.ui;

import util.random.RNG;

import java.awt.*;

/**
 * What the lab window offers to the problem view it shows.
 */
public interface ViewHost {

    // shows a message in the status bar
    void status(String message);

    // reports a problem to the user, e.g. an edit that would make the instance invalid
    void error(String message);

    // the lab's random number generator, seeded from the seed in the tool bar (for edits that need randomness)
    RNG rng();

    // the parent of dialogs
    Window window();
}
