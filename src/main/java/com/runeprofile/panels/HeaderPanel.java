package com.runeprofile.panels;

import com.runeprofile.Icon;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HeaderPanel extends JPanel {
    private static final int iconSize = 16;
    private final JPanel buttonsContainer;

    @Inject
    public HeaderPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("RuneProfile");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());

        buttonsContainer = new JPanel();
        buttonsContainer.setLayout(new GridLayout(1, 2, 4, 0));

        addHeaderButton(Icon.DISCORD.getIcon(iconSize, iconSize), "Join the Discord", "https://discord.com/invite/6XgBcePAfj");
        addHeaderButton(Icon.WEB.getIcon(iconSize, iconSize), "Visit the website.", "https://runeprofile.com");
        addHeaderButton(Icon.GITHUB.getIcon(iconSize, iconSize), "Report issues or contribute.", "https://github.com/ReinhardtR/runeprofile");

        add(title, BorderLayout.WEST);
        add(buttonsContainer, BorderLayout.EAST);
    }

    private void addHeaderButton(ImageIcon icon, String tooltip, String url) {
        JButton button = new JButton();
        SwingUtil.removeButtonDecorations(button);

        button.setIcon(icon);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> LinkBrowser.browse(url));

        button.setUI(new BasicButtonUI());
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        });

        buttonsContainer.add(button);
    }
}
