package problems.pecto.aco.gui;

import javax.swing.JEditorPane;
import javax.swing.UIManager;
import java.awt.Dimension;

/**
 * A read-only block of HTML text that wraps to the width it is given (a JLabel with HTML needs a fixed width).
 */
final class Html extends JEditorPane {

    Html() {
        super("text/html", "");
        setEditable(false);
        setOpaque(false);
        setFocusable(false);
        setBorder(null);
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        setFont(UIManager.getFont("Label.font"));
    }

    void set(String body) {
        setText("<html><body>" + body + "</body></html>");
    }

    // as wide as the container makes it, as high as the text then needs
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (getParent() != null && getParent().getWidth() > 0) {
            int w = getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right;
            setSize(new Dimension(w, Short.MAX_VALUE));
            d = new Dimension(w, super.getPreferredSize().height);
        }
        return d;
    }
}
